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
 * Created by IntelliJ IDEA.
 * User: sebas
 * Date: Dec 10, 2009
 * Time: 12:20:22 PM
 * To change this template use File | Settings | File Templates.
 */
public class ServerTransactionState extends TransactionState {

    ServerTransactionState(String name) {
        super(name);
    }

    final public void enter(TransactionBase transaction, boolean reenter) {
        enter((ServerTransaction) transaction, reenter);
    }

    final public void exit(TransactionBase transaction, boolean forReenter) {
        exit((ServerTransaction) transaction, forReenter);
    }

    public void enter(ServerTransaction transaction, boolean reenter) {

    }

    public void exit(ServerTransaction transaction, boolean forReenter) {

    }

    public String toString() {
        return "RtServerTransactionState:" + getName();
    }

}