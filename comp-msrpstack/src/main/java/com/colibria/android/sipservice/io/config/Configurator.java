/*
 *
 * Copyright (C) 2010 Colibria AS
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.colibria.android.sipservice.io.config;


/**
 * @author Sebastian Dehne
 */
public class Configurator {

    private static volatile int bufferSize = 1024 * 8;

    private static volatile boolean useFQDN = false;

    private static volatile int maxThreadBlock = 10;

    private static volatile int qosDefaultInPolicyBurst = 5;

    private static volatile int qoDefaultInPolicyDelay = 2 * 1000;

    private static volatile int maxOutboundQueueSize = 2048 * 10;

    public static int getBufferSize() {
        return bufferSize;
    }

    public static boolean isUseFQDN() {
        return useFQDN;
    }

    public static int getMaxThreadBlock() {
        return maxThreadBlock;
    }

    public static int getQosDefaultInPolicyBurst() {
        return qosDefaultInPolicyBurst;
    }

    public static int getQoDefaultInPolicyDelay() {
        return qoDefaultInPolicyDelay;
    }

    public static int getMaxOutboundQueueSize() {
        return maxOutboundQueueSize;
    }
}
