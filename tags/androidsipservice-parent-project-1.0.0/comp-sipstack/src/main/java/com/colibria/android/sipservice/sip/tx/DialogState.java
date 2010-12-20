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

import java.io.ObjectStreamException;

public class DialogState {

    /**
     * Constructor for the DialogState
     *
     * @param dialogState The integer value for the DialogueState
     */
    private DialogState(int dialogState) {
        m_dialogState = dialogState;
        m_dialogStateArray[m_dialogState] = this;
    }

    /**
     * This method returns the object value of the DialogState
     *
     * @param dialogState The integer value of the DialogState
     * @return The DialogState Object
     */
    public static DialogState getObject(int dialogState) {
        if (dialogState >= 0 && dialogState < m_size) {
            return m_dialogStateArray[dialogState];
        } else {
            throw new IllegalArgumentException("Invalid dialogState value");
        }
    }

    /**
     * This method returns the integer value of the DialogState
     *
     * @return The integer value of the DialogState
     */
    public int getValue() {
        return m_dialogState;
    }


    /**
     * Returns the designated type as an alternative object to be used when
     * writing an object to a stream.
     * <p/>
     * This method would be used when for example serializing DialogState.EARLY
     * and deserializing it afterwards results again in DialogState.EARLY.
     * If you do not implement readResolve(), you would not get
     * DialogState.EARLY but an instance with similar content.
     *
     * @return the DialogState
     * @throws java.io.ObjectStreamException
     */
    private Object readResolve() throws ObjectStreamException {
        return m_dialogStateArray[m_dialogState];
    }


    /**
     * Compare this dialog state for equality with another.
     *
     * @param obj the object to compare this with.
     * @return <code>true</code> if <code>obj</code> is an instance of this class
     *         representing the same dialog state as this, <code>false</code> otherwise.
     * @since 1.2
     */
    public boolean equals(Object obj) {
        if (obj == this) return true;

        return (obj instanceof DialogState) && ((DialogState) obj).m_dialogState == m_dialogState;
    }

    /**
     * Get a hash code value for this dialog state.
     *
     * @return a hash code value.
     * @since 1.2
     */
    public int hashCode() {
        return m_dialogState;
    }


    /**
     * This method returns a string version of this class.
     *
     * @return The string version of the DialogState
     */
    public String toString() {
        String text = "";
        switch (m_dialogState) {
            case _EARLY:
                text = "Early Dialog";
                break;
            case _CONFIRMED:
                text = "Confirmed Dialog";
                break;
            case _COMPLETED:
                text = "Completed Dialog";
                break;
            case _TERMINATED:
                text = "Terminated Dialog";
                break;
            default:
                text = "Error while printing Dialog State";
                break;
        }
        return text;
    }

    // internal variables
    private int m_dialogState;
    private static int m_size = 4;
    private static DialogState[] m_dialogStateArray = new DialogState[m_size];

    /**
     * This constant value indicates the internal value of the "Early"
     * constant.
     * <br>This constant has an integer value of 0.
     */
    public static final int _EARLY = 0;

    /**
     * This constant value indicates that the dialog state is "Early".
     */
    public final static DialogState EARLY = new DialogState(_EARLY);

    /**
     * This constant value indicates the internal value of the "Confirmed"
     * constant.
     * <br>This constant has an integer value of 1.
     */
    public static final int _CONFIRMED = 1;

    /**
     * This constant value indicates that the dialog state is "Confirmed".
     */
    public final static DialogState CONFIRMED = new DialogState(_CONFIRMED);

    /**
     * This constant value indicates the internal value of the "Completed"
     * constant.
     * <br>This constant has an integer value of 2.
     *
     * @deprecated Since v1.2. This state does not exist in a dialog.
     */
    public static final int _COMPLETED = 2;

    /**
     * This constant value indicates that the dialog state is "Completed".
     *
     * @deprecated Since v1.2. This state does not exist in a dialog.
     */
    public final static DialogState COMPLETED = new DialogState(_COMPLETED);

    /**
     * This constant value indicates the internal value of the "Terminated"
     * constant.
     * <br>This constant has an integer value of 3.
     */
    public static final int _TERMINATED = 3;

    /**
     * This constant value indicates that the dialog state is "Terminated".
     */
    public final static DialogState TERMINATED = new DialogState(_TERMINATED);


}
