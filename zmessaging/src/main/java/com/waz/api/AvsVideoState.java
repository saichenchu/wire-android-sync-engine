/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.api;

import com.waz.call.FlowManager;

public enum AvsVideoState {
    STARTED(FlowManager.VIDEO_STATE_STARTED),
    STOPPED(FlowManager.VIDEO_STATE_STOPPED);

    public final int state;

    AvsVideoState(int state) {
        this.state = state;
    }

    public static AvsVideoState fromState(int state) {
        for (AvsVideoState value : AvsVideoState.values()) {
            if (value.state == state) {
                return value;
            }
        }
        throw new IllegalArgumentException("unknown/invalid state: " + state);
    }
}
