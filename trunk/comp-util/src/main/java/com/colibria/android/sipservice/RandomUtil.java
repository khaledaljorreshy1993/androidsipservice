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
package com.colibria.android.sipservice;

import java.util.Random;

/**
 * @author Sebastian Dehne
 */
public class RandomUtil {

    private static final char[] hexDigits = {'0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    private static final char[] chars = {'0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'o', 'v', 'w', 'x', 'y', 'z',
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'O', 'V', 'W', 'X', 'Y', 'Z'
    };


    private static Random random = new Random();

    /**
     * Gets a random number from 0 (including) to "max" (excluding) argument, i.e.
     * returns a value less than "max".
     *
     * @param maxValue maxValue
     * @return result
     */
    public static int random(int maxValue) {
        return random.nextInt(maxValue);
    }

    /**
     * Generate random hex string of wanted size
     *
     * @param size the size
     * @return hex string
     */
    public static String randomHexString(int size) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < size; i++) {
            sb.append(hexDigits[random(hexDigits.length)]);
        }
        return sb.toString();
    }

    public static String nextRandomId() {
        return nextRandomId(16);
    }

    public static String nextRandomId(int size) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < size; i++) {
            sb.append(chars[random(chars.length)]);
        }
        return sb.toString();
    }
}
