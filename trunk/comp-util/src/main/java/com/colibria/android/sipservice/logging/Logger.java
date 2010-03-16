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
package com.colibria.android.sipservice.logging;

/**
 * @author Sebastian Dehne
 */
public class Logger {

    public enum Level {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    private static volatile ILogger LOGGER_IMPL;

    public static void setLOGGER_IMPL(ILogger LOGGER_IMPL) {
        Logger.LOGGER_IMPL = LOGGER_IMPL;
    }

    public static ILogger getLOGGER_IMPL() {
        return LOGGER_IMPL;
    }

    public static void d(String tag, String message) {
        d(tag, message, null);
    }

    public static void d(String tag, String message, Throwable t) {
        LOGGER_IMPL.write(Level.DEBUG, tag, message, t);
    }

    public static void i(String tag, String message) {
        i(tag, message, null);
    }

    public static void i(String tag, String message, Throwable t) {
        LOGGER_IMPL.write(Level.INFO, tag, message, t);
    }

    public static void w(String tag, String message) {
        i(tag, message, null);
    }

    public static void w(String tag, String message, Throwable t) {
        LOGGER_IMPL.write(Level.WARN, tag, message, t);
    }

    public static void e(String tag, String message) {
        e(tag, message, null);
    }

    public static void e(String tag, String message, Throwable t) {
        LOGGER_IMPL.write(Level.ERROR, tag, message, t);
    }

    public static boolean isLoggable(Level level, String tag) {
        // todo
        return true;
    }

}
