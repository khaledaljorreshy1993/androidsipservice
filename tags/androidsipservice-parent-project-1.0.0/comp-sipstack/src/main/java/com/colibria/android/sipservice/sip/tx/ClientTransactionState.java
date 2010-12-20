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
 * 
 */
public class ClientTransactionState extends TransactionState {

    public ClientTransactionState(String name) {
            super(name);
        }

        final public void enter(TransactionBase transaction, boolean reenter) {
            enter((ClientTransaction) transaction, reenter);
        }

        final public void exit(TransactionBase transaction, boolean forReenter) {
            exit((ClientTransaction) transaction, forReenter);
        }

        public void enter(ClientTransaction transaction, boolean reenter) {

        }

        public void exit(ClientTransaction transaction, boolean forReenter) {

        }

        public String toString() {
            return "RtClientTransactionState:" + getName();
        }

}
