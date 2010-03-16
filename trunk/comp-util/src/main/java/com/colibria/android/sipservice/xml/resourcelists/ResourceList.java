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
package com.colibria.android.sipservice.xml.resourcelists;

import java.util.List;

/**
 * @author Sebastian Dehne
 */
public class ResourceList extends AbstractEntry {

    private final List<AbstractEntry> lists;
    private final String name;

    public ResourceList(List<AbstractEntry> lists, String displayName, String name) {
        super(displayName);
        this.lists = lists;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public List<AbstractEntry> getLists() {
        return lists;
    }
}
