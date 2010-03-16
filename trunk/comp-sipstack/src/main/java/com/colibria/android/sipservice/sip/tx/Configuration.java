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
package com.colibria.android.sipservice.sip.tx;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Sebastian Dehne
 */
public class Configuration {
    private volatile static boolean automaticDialogSupport = true;
    private volatile static boolean setAliasForIncoming = false;

    /**
     * Configures wether dialogs should be created automatically.
     * Should be switches off in case the applications acts as a proxy
     *
     * @param automaticDialogSupport true if dialogs should be created automatically. Else false
     */
    public static void setAutomaticDialogSupport(boolean automaticDialogSupport) {
        Configuration.automaticDialogSupport = automaticDialogSupport;
    }

    /**
     * Retrieves wether dialogs should be created automatically.
     *
     * @return true if dialogs should be created automatically. Else false.
     */
    public static boolean isAutomaticDialogSupport() {
        return automaticDialogSupport;
    }

    private static volatile int tcpConnectionTimeout = 120 * 1000;

    public static int getTcpConnectionTimeout() {
        return tcpConnectionTimeout;
    }

    public static void setTcpConnectionTimeout(int tcpConnectionTimeout) {
        Configuration.tcpConnectionTimeout = tcpConnectionTimeout;
    }

    private static final int ONE_SEC = 1000;
    private static volatile int T1 = 500;
    private static volatile int T2 = 4000;
    private static volatile int T4 = 5000;

    private static volatile int TIMER_A_IVAL = T1;
    private static volatile int TIMER_B = 64 * T1;
    private static volatile int TIMER_C = 3 * 60 * ONE_SEC;
    private static volatile int TIMER_D_UDP = 32 * ONE_SEC;
    private static volatile int TIMER_D_TCP = 0;
    private static volatile int TIMER_E_IVAL = T1;
    private static volatile int TIMER_F = 64 * T1;
    private static volatile int TIMER_G_IVAL = T1;
    private static volatile int TIMER_H = 64 * T1;
    private static volatile int TIMER_I_UDP = T4;
    private static volatile int TIMER_I_TCP = 0;
    private static volatile int TIMER_J_UDP = 64 * T1;
    private static volatile int TIMER_J_TCP = 0;
    private static volatile int TIMER_K_UDP = T4;
    private static volatile int TIMER_K_TCP = 0;

    private static volatile boolean stripRouteHeader = false;
    private static volatile int udpMaxContentSize = 1300;
    private static volatile List<String> transportPriorityOrder = Collections.unmodifiableList(Arrays.asList("UDP", "TCP", "TLS"));
    private static volatile List<String> statefullRequests = Collections.singletonList("ALL");

    public static int getT1() {
        return T1;
    }

    /**
     * Sets T1 without changing any of the timers.
     *
     * @param t1 in milliseconds
     */
    public static void setT1(int t1) {
        T1 = t1;
    }

    /**
     * Sets T1 and updates all timers accordingly.
     *
     * @param t1 in milliseconds
     */
    public static void setT1AndUpdate(int t1) {
        T1 = t1;
        TIMER_A_IVAL = T1;
        TIMER_B = 64 * T1;
        TIMER_E_IVAL = T1;
        TIMER_F = 64 * T1;
        TIMER_G_IVAL = T1;
        TIMER_H = 64 * T1;
        TIMER_J_UDP = 64 * T1;
    }

    public static int getT2() {
        return T2;
    }

    /**
     * Sets T2 without changing any of the timers.
     *
     * @param t2 in milliseconds
     */
    public static void setT2(int t2) {
        T2 = t2;
    }

    public static int getT4() {
        return T4;
    }

    /**
     * Sets T4 without changing any of the timers.
     *
     * @param t4 in milliseconds
     */
    public static void setT4(int t4) {
        T4 = t4;
    }

    /**
     * Sets T4 and updates all timers accordingly.
     *
     * @param t4 in milliseconds
     */
    public static void setT4AndUpdate(int t4) {
        T4 = t4;
        TIMER_I_UDP = T4;
        TIMER_K_UDP = T4;
    }

    public static int getTIMER_A_IVAL() {
        return TIMER_A_IVAL;
    }

    /**
     * Sets timerA
     *
     * @param TIMER_A_IVAL in milliseconds
     */
    public static void setTIMER_A_IVAL(int TIMER_A_IVAL) {
        Configuration.TIMER_A_IVAL = TIMER_A_IVAL;
    }

    public static int getTIMER_B() {
        return TIMER_B;
    }

    /**
     * Sets timerB
     *
     * @param TIMER_B in milliseconds
     */
    public static void setTIMER_B(int TIMER_B) {
        Configuration.TIMER_B = TIMER_B;
    }

    public static int getTIMER_C() {
        return TIMER_C;
    }

    /**
     * Sets timerC
     *
     * @param TIMER_C in milliseconds
     */
    public static void setTIMER_C(int TIMER_C) {
        Configuration.TIMER_C = TIMER_C;
    }

    public static int getTIMER_D_UDP() {
        return TIMER_D_UDP;
    }

    /**
     * Sets timerD for UDP
     *
     * @param TIMER_D_UDP in milliseconds
     */
    public static void setTIMER_D_UDP(int TIMER_D_UDP) {
        Configuration.TIMER_D_UDP = TIMER_D_UDP;
    }

    public static int getTIMER_D_TCP() {
        return TIMER_D_TCP;
    }

    /**
     * Sets timerD for TCP
     *
     * @param TIMER_D_TCP in milliseconds
     */
    public static void setTIMER_D_TCP(int TIMER_D_TCP) {
        Configuration.TIMER_D_TCP = TIMER_D_TCP;
    }

    public static int getTIMER_E_IVAL() {
        return TIMER_E_IVAL;
    }

    /**
     * Sets timerE
     *
     * @param TIMER_E_IVAL in milliseconds
     */
    public static void setTIMER_E_IVAL(int TIMER_E_IVAL) {
        Configuration.TIMER_E_IVAL = TIMER_E_IVAL;
    }

    public static int getTIMER_F() {
        return TIMER_F;
    }

    /**
     * Sets timerF
     *
     * @param TIMER_F in milliseconds
     */
    public static void setTIMER_F(int TIMER_F) {
        Configuration.TIMER_F = TIMER_F;
    }

    public static int getTIMER_G_IVAL() {
        return TIMER_G_IVAL;
    }

    /**
     * Sets timerG
     *
     * @param TIMER_G_IVAL in milliseconds
     */
    public static void setTIMER_G_IVAL(int TIMER_G_IVAL) {
        Configuration.TIMER_G_IVAL = TIMER_G_IVAL;
    }

    public static int getTIMER_H() {
        return TIMER_H;
    }

    /**
     * Sets timerH
     *
     * @param TIMER_H in milliseconds
     */
    public static void setTIMER_H(int TIMER_H) {
        Configuration.TIMER_H = TIMER_H;
    }

    public static int getTIMER_I_UDP() {
        return TIMER_I_UDP;
    }

    /**
     * Sets timerI for UDP
     *
     * @param TIMER_I_UDP in milliseconds
     */
    public static void setTIMER_I_UDP(int TIMER_I_UDP) {
        Configuration.TIMER_I_UDP = TIMER_I_UDP;
    }

    public static int getTIMER_I_TCP() {
        return TIMER_I_TCP;
    }

    /**
     * Sets timerI for TCP
     *
     * @param TIMER_I_TCP in milliseconds
     */
    public static void setTIMER_I_TCP(int TIMER_I_TCP) {
        Configuration.TIMER_I_TCP = TIMER_I_TCP;
    }

    public static int getTIMER_J_UDP() {
        return TIMER_J_UDP;
    }

    /**
     * Sets timerJ for UDP
     *
     * @param TIMER_J_UDP in milliseconds
     */
    public static void setTIMER_J_UDP(int TIMER_J_UDP) {
        Configuration.TIMER_J_UDP = TIMER_J_UDP;
    }

    public static int getTIMER_J_TCP() {
        return TIMER_J_TCP;
    }

    /**
     * Sets timerJ for TCP
     *
     * @param TIMER_J_TCP in milliseconds
     */
    public static void setTIMER_J_TCP(int TIMER_J_TCP) {
        Configuration.TIMER_J_TCP = TIMER_J_TCP;
    }

    public static int getTIMER_K_UDP() {
        return TIMER_K_UDP;
    }

    /**
     * Sets timerK for UDP
     *
     * @param TIMER_K_UDP in milliseconds
     */
    public static void setTIMER_K_UDP(int TIMER_K_UDP) {
        Configuration.TIMER_K_UDP = TIMER_K_UDP;
    }

    public static int getTIMER_K_TCP() {
        return TIMER_K_TCP;
    }

    /**
     * Sets timerK for TCP
     *
     * @param TIMER_K_TCP in milliseconds
     */
    public static void setTIMER_K_TCP(int TIMER_K_TCP) {
        Configuration.TIMER_K_TCP = TIMER_K_TCP;
    }

    public static boolean isStripRouteHeader() {
        return stripRouteHeader;
    }

    public static void setStripRouteHeader(boolean stripRouteHeader) {
        Configuration.stripRouteHeader = stripRouteHeader;
    }

    public static int getUdpMaxContentSize() {
        return udpMaxContentSize;
    }

    public static void setUdpMaxContentSize(int udpMaxContentSize) {
        Configuration.udpMaxContentSize = udpMaxContentSize;
    }

    public static List<String> getTransportPriorityOrder() {
        return transportPriorityOrder;
    }

    public static void setTransportPriorityOrder(String transportPriorityOrder) {
        String[] parts = transportPriorityOrder.split("-");
        List<String> result = new LinkedList<String>();
        result.addAll(Arrays.asList(parts));
        Configuration.transportPriorityOrder = Collections.unmodifiableList(result);
    }

    public static List<String> getStatefullRequests() {
        return statefullRequests;
    }

    public static void setStatefullRequests(List<String> statefullRequests) {
        Configuration.statefullRequests = statefullRequests;
    }

    public static boolean isSetAliasForIncoming() {
        return setAliasForIncoming;
    }

    public static void setSetAliasForIncoming(boolean setAliasForIncoming) {
        Configuration.setAliasForIncoming = setAliasForIncoming;
    }

    private static int tcpSetupTimeout = 10 * 1000;

    public static int getTcpSetupTimeout() {
        return tcpSetupTimeout;
    }

    public static void setTcpSetupTimeout(int tcpSetupTimeout) {
        Configuration.tcpSetupTimeout = tcpSetupTimeout;
    }


    private static int maxTcpConnectionsThreshold = 100;

    public static int getMaxTcpConnectionsThreshold() {
        return maxTcpConnectionsThreshold;
    }

    public static void setMaxTcpConnectionsThreshold(int maxTcpConnectionsThreshold) {
        Configuration.maxTcpConnectionsThreshold = maxTcpConnectionsThreshold;
    }

    public static volatile boolean answerKeepAlives = true;

    public static boolean isAnswerKeepAlives() {
        return answerKeepAlives;
    }

    public static void setAnswerKeepAlives(boolean answerKeepAlives) {
        Configuration.answerKeepAlives = answerKeepAlives;
    }

}
