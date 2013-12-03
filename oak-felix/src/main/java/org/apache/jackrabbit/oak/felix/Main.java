/*************************************************************************
 *
 * ADOBE CONFIDENTIAL
 * ___________________
 *
 *  Copyright ${today.year} Adobe Systems Incorporated
 *  All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Adobe Systems Incorporated and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Adobe Systems Incorporated and its
 * suppliers and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Adobe Systems Incorporated.
 **************************************************************************/
package org.apache.jackrabbit.oak.felix;

import java.util.HashMap;
import java.util.Map;

import org.apache.felix.framework.FrameworkFactory;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.launch.Framework;

/**
 * {@code Main}...
 */
public class Main {

    public static void main(String[] args) throws Exception {

        FrameworkFactory factory = new FrameworkFactory();
        Map<String, String> config = new HashMap<String, String>();
        Framework fwk = factory.newFramework(config);
        fwk.init();
        // Use the system bundle context to process the auto-deploy
        // and auto-install/auto-start properties.
        //AutoProcessor.process(configProps, m_fwk.getBundleContext());
        FrameworkEvent event;
        do
        {
            // Start the framework.
            fwk.start();
            // Wait for framework to stop to exit the VM.
            event = fwk.waitForStop(0);
        }
        // If the framework was updated, then restart it.
        while (event.getType() == FrameworkEvent.STOPPED_UPDATE);
        // Otherwise, exit.
    }
}