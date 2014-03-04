/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
#import "jni_util.h"

#import <Cocoa/Cocoa.h>
#import <JavaNativeFoundation/JavaNativeFoundation.h>

#import "GeomUtilities.h"
#import "ThreadUtilities.h"

#import "sun_lwawt_macosx_CImage.h"


static void CImage_CopyArrayIntoNSImageRep
(jint *srcPixels, jint *dstPixels, int width, int height)
{
    int x, y;
    // TODO: test this on big endian systems (not sure if its correct)...
    for (y = 0; y < height; y++) {
        for (x = 0; x < width; x++) {
            jint pix = srcPixels[x];
            jint a = (pix >> 24) & 0xff;
            jint r = (pix >> 16) & 0xff;
            jint g = (pix >>  8) & 0xff;
            jint b = (pix      ) & 0xff;
            dstPixels[x] = (b << 24) | (g << 16) | (r << 8) | a;
        }
        srcPixels += width; // TODO: use explicit scanStride
        dstPixels += width;
    }
}

static void CImage_CopyNSImageIntoArray
(NSImage *srcImage, jint *dstPixels, NSRect fromRect, NSRect toRect)
{
    int width = toRect.size.width;
    int height = toRect.size.height;
    CGColorSpaceRef colorspace = CGColorSpaceCreateDeviceRGB();
    CGContextRef cgRef = CGBitmapContextCreate(dstPixels, width, height,
                                8, width * 4, colorspace,
                                kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Host);
    CGColorSpaceRelease(colorspace);
    NSGraphicsContext *context = [NSGraphicsContext graphicsContextWithGraphicsPort:cgRef flipped:NO];
    CGContextRelease(cgRef);
    NSGraphicsContext *oldContext = [[NSGraphicsContext currentContext] retain];
    [NSGraphicsContext setCurrentContext:context];
    [srcImage drawInRect:toRect
                fromRect:fromRect
               operation:NSCompositeSourceOver
                fraction:1.0];
    [NSGraphicsContext setCurrentContext:oldContext];
    [oldContext release];
}

static NSBitmapImageRep* CImage_CreateImageRep(JNIEnv *env, jintArray buffer, jint width, jint height)
{
    NSBitmapImageRep* imageRep = [[NSBitmapImageRep alloc] initWithBitmapDataPlanes:NULL
                                                                         pixelsWide:width
                                                                         pixelsHigh:height
                                                                      bitsPerSample:8
                                                                    samplesPerPixel:4
                                                                           hasAlpha:YES
                                                                           isPlanar:NO
                                                                     colorSpaceName:NSDeviceRGBColorSpace
                                                                       bitmapFormat:NSAlphaFirstBitmapFormat
                                                                        bytesPerRow:width*4 // TODO: use explicit scanStride
                                                                       bitsPerPixel:32];

    jint *imgData = (jint *)[imageRep bitmapData];
    if (imgData == NULL) return 0L;

    jint *src = (*env)->GetPrimitiveArrayCritical(env, buffer, NULL);
    if (src == NULL) return 0L;

    CImage_CopyArrayIntoNSImageRep(src, imgData, width, height);

    (*env)->ReleasePrimitiveArrayCritical(env, buffer, src, JNI_ABORT);

    return imageRep;
}

/*
 * Class:     sun_lwawt_macosx_CImage
 * Method:    nativeCreateNSImageFromArray
 * Signature: ([III)J
 */
JNIEXPORT jlong JNICALL Java_sun_lwawt_macosx_CImage_nativeCreateNSImageFromArray
(JNIEnv *env, jclass klass, jintArray buffer, jint width, jint height)
{
    jlong result = 0L;

JNF_COCOA_ENTER(env);

    NSBitmapImageRep* imageRep = CImage_CreateImageRep(env, buffer, width, height);
    if (imageRep) {
        NSImage *nsImage = [[NSImage alloc] initWithSize:NSMakeSize(width, height)];
        [nsImage addRepresentation:imageRep];
        [imageRep release];

        if (nsImage != nil) {
            CFRetain(nsImage); // GC
        }

        result = ptr_to_jlong(nsImage);
    }

JNF_COCOA_EXIT(env);

    return result;
}

/*
 * Class:     sun_lwawt_macosx_CImage
 * Method:    nativeCreateNSImageFromArrays
 * Signature: ([[I[I[I)J
 */
JNIEXPORT jlong JNICALL Java_sun_lwawt_macosx_CImage_nativeCreateNSImageFromArrays
(JNIEnv *env, jclass klass, jobjectArray buffers, jintArray widths, jintArray heights)
{
    jlong result = 0L;

JNF_COCOA_ENTER(env);

    jsize num = (*env)->GetArrayLength(env, buffers);
    NSMutableArray * reps = [NSMutableArray arrayWithCapacity: num];

    jint * ws = (*env)->GetIntArrayElements(env, widths, NULL);
    if (ws != NULL) {
        jint * hs = (*env)->GetIntArrayElements(env, heights, NULL);
        if (hs != NULL) {
            jsize i;
            for (i = 0; i < num; i++) {
                jintArray buffer = (*env)->GetObjectArrayElement(env, buffers, i);

                NSBitmapImageRep* imageRep = CImage_CreateImageRep(env, buffer, ws[i], hs[i]);
                if (imageRep) {
                    [reps addObject: imageRep];
                }
            }

            (*env)->ReleaseIntArrayElements(env, heights, hs, JNI_ABORT);
        }
        (*env)->ReleaseIntArrayElements(env, widths, ws, JNI_ABORT);
    }
    if ([reps count]) {
        NSImage *nsImage = [[NSImage alloc] initWithSize:NSMakeSize(0, 0)];
        [nsImage addRepresentations: reps];

        if (nsImage != nil) {
            CFRetain(nsImage); // GC
        }

        result = ptr_to_jlong(nsImage);
    }

JNF_COCOA_EXIT(env);

    return result;
}

/*
 * Class:     sun_lwawt_macosx_CImage
 * Method:    nativeCreateNSImageFromIconSelector
 * Signature: (I)J
 */
JNIEXPORT jlong JNICALL Java_sun_lwawt_macosx_CImage_nativeCreateNSImageFromIconSelector
(JNIEnv *env, jclass klass, jint selector)
{
    NSImage *image = nil;

JNF_COCOA_ENTER(env);

    IconRef iconRef;
    if (noErr == GetIconRef(kOnSystemDisk, kSystemIconsCreator, selector, &iconRef)) {
        image = [[NSImage alloc] initWithIconRef:iconRef];
        if (image) CFRetain(image); // GC
        ReleaseIconRef(iconRef);
    }

JNF_COCOA_EXIT(env);

    return ptr_to_jlong(image);
}

/*
 * Class:     sun_lwawt_macosx_CImage
 * Method:    nativeCreateNSImageFromFileContents
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_sun_lwawt_macosx_CImage_nativeCreateNSImageFromFileContents
(JNIEnv *env, jclass klass, jstring file)
{
    NSImage *image = nil;

JNF_COCOA_ENTER(env);

    NSString *path = JNFNormalizedNSStringForPath(env, file);
    image = [[NSImage alloc] initByReferencingFile:path];
    if (image) CFRetain(image); // GC

JNF_COCOA_EXIT(env);

    return ptr_to_jlong(image);
}

/*
 * Class:     sun_lwawt_macosx_CImage
 * Method:    nativeCreateNSImageOfFileFromLaunchServices
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_sun_lwawt_macosx_CImage_nativeCreateNSImageOfFileFromLaunchServices
(JNIEnv *env, jclass klass, jstring file)
{
    __block NSImage *image = nil;

JNF_COCOA_ENTER(env);

    NSString *path = JNFNormalizedNSStringForPath(env, file);
    [ThreadUtilities performOnMainThreadWaiting:YES block:^(){
        image = [[NSWorkspace sharedWorkspace] iconForFile:path];
        [image setScalesWhenResized:TRUE];
        if (image) CFRetain(image); // GC
    }];

JNF_COCOA_EXIT(env);

    return ptr_to_jlong(image);
}

/*
 * Class:     sun_lwawt_macosx_CImage
 * Method:    nativeCreateNSImageFromImageName
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_sun_lwawt_macosx_CImage_nativeCreateNSImageFromImageName
(JNIEnv *env, jclass klass, jstring name)
{
    NSImage *image = nil;

JNF_COCOA_ENTER(env);

    image = [NSImage imageNamed:JNFJavaToNSString(env, name)];
    if (image) CFRetain(image); // GC

JNF_COCOA_EXIT(env);

    return ptr_to_jlong(image);
}

/*
 * Class:     sun_lwawt_macosx_CImage
 * Method:    nativeCopyNSImageIntoArray
 * Signature: (J[IIIII)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CImage_nativeCopyNSImageIntoArray
(JNIEnv *env, jclass klass, jlong nsImgPtr, jintArray buffer, jint sw, jint sh,
                 jint dw, jint dh)
{
JNF_COCOA_ENTER(env);

    NSImage *img = (NSImage *)jlong_to_ptr(nsImgPtr);
    jint *dst = (*env)->GetPrimitiveArrayCritical(env, buffer, NULL);
    if (dst) {
        NSRect fromRect = NSMakeRect(0, 0, sw, sh);
        NSRect toRect = NSMakeRect(0, 0, dw, dh);
        CImage_CopyNSImageIntoArray(img, dst, fromRect, toRect);
        (*env)->ReleasePrimitiveArrayCritical(env, buffer, dst, JNI_ABORT);
    }

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CImage
 * Method:    nativeGetNSImageSize
 * Signature: (J)Ljava/awt/geom/Dimension2D;
 */
JNIEXPORT jobject JNICALL Java_sun_lwawt_macosx_CImage_nativeGetNSImageSize
(JNIEnv *env, jclass klass, jlong nsImgPtr)
{
    jobject size = NULL;

JNF_COCOA_ENTER(env);

    size = NSToJavaSize(env, [(NSImage *)jlong_to_ptr(nsImgPtr) size]);

JNF_COCOA_EXIT(env);

    return size;
}

/*
 * Class:     sun_lwawt_macosx_CImage
 * Method:    nativeSetNSImageSize
 * Signature: (JDD)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CImage_nativeSetNSImageSize
(JNIEnv *env, jclass clazz, jlong image, jdouble w, jdouble h)
{
    if (!image) return;
    NSImage *i = (NSImage *)jlong_to_ptr(image);

JNF_COCOA_ENTER(env);

    [i setScalesWhenResized:TRUE];
    [i setSize:NSMakeSize(w, h)];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CImage
 * Method:    nativeResizeNSImageRepresentations
 * Signature: (JDD)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CImage_nativeResizeNSImageRepresentations
(JNIEnv *env, jclass clazz, jlong image, jdouble w, jdouble h)
{
    if (!image) return;
    NSImage *i = (NSImage *)jlong_to_ptr(image);
    
JNF_COCOA_ENTER(env);
    
    NSImageRep *imageRep = nil;
    NSArray *imageRepresentations = [i representations];
    NSEnumerator *imageEnumerator = [imageRepresentations objectEnumerator];
    while ((imageRep = [imageEnumerator nextObject]) != nil) {
        [imageRep setSize:NSMakeSize(w, h)];
    }
    
JNF_COCOA_EXIT(env);
}

NSComparisonResult getOrder(BOOL order){
    return (NSComparisonResult) (order ? NSOrderedAscending : NSOrderedDescending);
}

/*
 * Class:     sun_lwawt_macosx_CImage
 * Method:    nativeGetNSImageRepresentationsCount
 * Signature: (JDD)[Ljava/awt/geom/Dimension2D;
 */
JNIEXPORT jobjectArray JNICALL
                  Java_sun_lwawt_macosx_CImage_nativeGetNSImageRepresentationSizes
(JNIEnv *env, jclass clazz, jlong image, jdouble w, jdouble h)
{
    if (!image) return NULL;
    jobjectArray jreturnArray = NULL;
    NSImage *img = (NSImage *)jlong_to_ptr(image);

JNF_COCOA_ENTER(env);
        
    NSArray *imageRepresentations = [img representations];
    if([imageRepresentations count] == 0){
        return NULL;
    }
    
    NSArray *sortedImageRepresentations = [imageRepresentations
                    sortedArrayUsingComparator: ^(id obj1, id obj2) {
        
        NSImageRep *imageRep1 = (NSImageRep *) obj1;
        NSImageRep *imageRep2 = (NSImageRep *) obj2;
        NSSize size1 = [imageRep1 size];
        NSSize size2 = [imageRep2 size];
        
        if (NSEqualSizes(size1, size2)) {
            return getOrder([imageRep1 pixelsWide] <= [imageRep2 pixelsWide] &&
                            [imageRep1 pixelsHigh] <= [imageRep2 pixelsHigh]);
        }

        return getOrder(size1.width <= size2.width && size1.height <= size2.height);
    }];

    NSMutableArray *sortedPixelSizes = [[NSMutableArray alloc] init];
    NSSize lastSize = [[sortedImageRepresentations lastObject] size];
    
    NSUInteger i = [sortedImageRepresentations indexOfObjectPassingTest:
               ^BOOL(id obj, NSUInteger idx, BOOL *stop) {
        NSSize imageRepSize = [obj size];
        return (w <= imageRepSize.width && h <= imageRepSize.height)
                   || NSEqualSizes(imageRepSize, lastSize);
    }];

    NSUInteger count = [sortedImageRepresentations count];
    i = (i == NSNotFound) ? count - 1 : i;
    NSSize bestFitSize = [[sortedImageRepresentations objectAtIndex: i] size];

    for(; i < count; i++){
        NSImageRep *imageRep = [sortedImageRepresentations objectAtIndex: i];

        if (!NSEqualSizes([imageRep size], bestFitSize)) {
            break;
        }

        NSSize pixelSize = NSMakeSize(
                                [imageRep pixelsWide], [imageRep pixelsHigh]);
        [sortedPixelSizes addObject: [NSValue valueWithSize: pixelSize]];
    }

    count = [sortedPixelSizes count];
    static JNF_CLASS_CACHE(jc_Dimension, "java/awt/Dimension");
    jreturnArray = JNFNewObjectArray(env, &jc_Dimension, count);
    CHECK_NULL_RETURN(jreturnArray, NULL);

    for(i = 0; i < count; i++){
        NSSize pixelSize = [[sortedPixelSizes objectAtIndex: i] sizeValue];

        (*env)->SetObjectArrayElement(env, jreturnArray, i,
                                      NSToJavaSize(env, pixelSize));
        JNU_CHECK_EXCEPTION_RETURN(env, NULL);
    }

JNF_COCOA_EXIT(env);

    return jreturnArray;
}