/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 6707406
 * @summary Tests color chooser with invalid UI
 * @author Sergey Malenkov
 */

import java.awt.Color;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JColorChooser;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.plaf.basic.BasicColorChooserUI;

public class Test6707406 extends BasicColorChooserUI implements PropertyChangeListener {
    public static void main(String[] args) throws Exception {
        test();
        for (LookAndFeelInfo laf : UIManager.getInstalledLookAndFeels()) {
            System.out.println(laf.getName());
            UIManager.setLookAndFeel(laf.getClassName());
            test();
        }
    }

    private static void test() {
        JColorChooser chooser = new JColorChooser();
        chooser.getUI().uninstallUI(chooser);
        new Test6707406().installUI(chooser);
        chooser.getSelectionModel().setSelectedColor(Color.BLUE);
    }

    @Override
    protected PropertyChangeListener createPropertyChangeListener() {
        return this;
    }

    public void propertyChange(PropertyChangeEvent event) {
    }
}
