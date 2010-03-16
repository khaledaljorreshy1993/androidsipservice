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

/**
 * @author Sebastian Dehne
 */
public class Constants {
    public static final int DEFAULT_PORT = 5060;

    // Added by Daniel J. Martinez Manzano <dani@dif.um.es>
    public static final int DEFAULT_TLS_PORT = 5061;

    /**
     * Prefix for the branch parameter that identifies
     * BIS 09 compatible branch strings. This indicates
     * that the branch may be as a global identifier for
     * identifying transactions.
     */
    public static final String BRANCH_MAGIC_COOKIE = "z9hG4bK";

    public static final String BRANCH_MAGIC_COOKIE_LOWER_CASE = "z9hg4bk";

    public static final String BRANCH_MAGIC_COOKIE_UPPER_CASE = "Z9HG4BK";

    /**
     * constant SIP_VERSION_STRING
     */
    public static final String SIP_VERSION_STRING = "SIP/2.0";
}
