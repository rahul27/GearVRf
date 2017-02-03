/*
 * Copyright 2016 Samsung Electronics Co., LTD
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gearvrf.io.cursor3d;

import android.view.KeyEvent;
import android.view.MotionEvent;
import org.gearvrf.GVRContext;
import org.gearvrf.GVRCursorController;
import org.gearvrf.GVRCursorController.ControllerEventListener;
import org.gearvrf.GVRSceneObject;
import org.gearvrf.SensorEvent;
import org.gearvrf.io.cursor3d.CursorAsset.Action;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class ObjectCursor extends Cursor {
    private static final String TAG = ObjectCursor.class.getSimpleName();
    private static final float POINT_CURSOR_NEAR_DEPTH = -1.0f;
    private Set<GVRSceneObject> intersecting;


    ObjectCursor(GVRContext context, CursorManager cursorManager) {
        super(context, CursorType.OBJECT, cursorManager);
        intersecting = new HashSet<GVRSceneObject>();
    }

    @Override
    void dispatchSensorEvent(SensorEvent event) {
        GVRSceneObject object = event.getObject();
        GVRCursorController controller = event.getCursorController();
        isControllerActive = event.isActive();
        boolean colliding = false;

        SelectableGroup selectableBehavior = (SelectableGroup) object.getComponent
                (SelectableGroup.getComponentType());
        if (selectableBehavior != null) {
            if (isColliding(object) && event.isOver()) {
                colliding = true;
            }
            if (colliding) {
                selectableBehavior.intersectingList.add(object);
            } else {
                selectableBehavior.intersectingList.remove(object);
                if (!selectableBehavior.intersectingList.isEmpty()) {
                    colliding = true;
                } else {
                    colliding = false;
                }
            }
            object = selectableBehavior.getParent();
        } else {
            if (isColliding(object)) {
                colliding = true;
            }
        }

        if (colliding) {
            intersecting.add(object);
        }

        if (object != null && colliding) {
            createAndSendCursorEvent(object, true, event.getHitX(), event.getHitY(),
                    event.getHitZ(), true, isControllerActive,
                    event.getCursorController().getKeyEvent(), controller.getMotionEvents());
        } else {
            createAndSendCursorEvent(object, false, event.getHitX(), event.getHitY(),
                    event.getHitZ(), event.isOver(), isControllerActive,
                    event.getCursorController().getKeyEvent(), controller.getMotionEvents());
        }
    }

    @Override
    ControllerEventListener getControllerEventListener() {
        return new ControllerEventListener() {
            @Override
            public void onEvent(GVRCursorController gvrCursorController) {
                // not used
            }
        };
    }

    private void createAndSendCursorEvent(GVRSceneObject sceneObject, boolean colliding, float
            hitX, float hitY, float hitZ, boolean isOver, boolean isActive, KeyEvent keyEvent,
                                          List<MotionEvent> motionEvents) {
        CursorEvent cursorEvent = CursorEvent.obtain();
        cursorEvent.setColliding(colliding);
        cursorEvent.setHitPoint(hitX, hitY, hitZ);
        cursorEvent.setCursorPosition(getPositionX(), getPositionY(), getPositionZ());
        cursorEvent.setCursorRotation(getRotationW(), getRotationX(), getRotationY(),
                getRotationZ());
        cursorEvent.setOver(isOver);
        cursorEvent.setObject(sceneObject);
        cursorEvent.setActive(isActive);
        cursorEvent.setCursor(this);
        cursorEvent.setKeyEvent(keyEvent);
        cursorEvent.setMotionEvents(motionEvents);

        if (colliding) {
            intersecting.add(sceneObject);
        } else {
            intersecting.remove(sceneObject);
        }

        if (!intersecting.isEmpty()) {
            if (isActive) {
                checkAndSetAsset(Action.CLICK);
            } else {
                checkAndSetAsset(Action.INTERSECT);
            }
        } else {

            checkAndSetAsset(Action.DEFAULT);
        }

        dispatchCursorEvent(cursorEvent);
    }


    @Override
    void setScale(float scale) {
        if (scale > MAX_CURSOR_SCALE) {
            return;
        }

        // place the cursor at half the depth scale
        super.setScale(scale / 2);

        if (ioDevice != null) {
            ioDevice.setNearDepth(POINT_CURSOR_NEAR_DEPTH);
        }
    }

    @Override
    void setIoDevice(IoDevice ioDevice) {
        super.setIoDevice(ioDevice);
        ioDevice.setNearDepth(POINT_CURSOR_NEAR_DEPTH);
    }

    @Override
    void setupIoDevice(IoDevice ioDevice) {
        super.setupIoDevice(ioDevice);
        ioDevice.setDisableRotation(false);
    }
}