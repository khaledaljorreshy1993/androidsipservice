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
package com.colibria.android.sipservice.sip.frameworks.clientpublisher.fsm;

import com.colibria.android.sipservice.fsm.Condition;
import com.colibria.android.sipservice.sip.frameworks.clientpublisher.ClientPublisher;

/**
 * @author Sebastian Dehne
 */
public class PublishCondition extends Condition<Signal, ClientPublisher> {

    private final Signal.Type type;

    public PublishCondition(Signal.Type type) {
        this.type = type;
    }

    public boolean satisfiedBy(Signal signal, ClientPublisher owner) {
        return signal.isType(type);
    }

    public String toString() {
        return type.toString();
    }

    public static final PublishCondition SEND_PUBLISH = new PublishCondition(Signal.Type.sendPublish);
    public static final PublishCondition SEND_REFRESH = new PublishCondition(Signal.Type.sendPublish){
        @Override
        public boolean satisfiedBy(Signal signal, ClientPublisher owner) {
            return super.satisfiedBy(signal, owner) && signal.getContent() == null;
        }
    };
    public static final PublishCondition RESPONSE_2xx = new PublishCondition(Signal.Type.response2xx);
    public static final PublishCondition RESPONSE_412 = new PublishCondition(Signal.Type.response412);
    public static final PublishCondition RESPONSE_423 = new PublishCondition(Signal.Type.response423);
    public static final PublishCondition TERMINATE = new PublishCondition(Signal.Type.terminate);

}
