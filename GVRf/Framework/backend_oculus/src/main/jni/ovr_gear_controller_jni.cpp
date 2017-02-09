/* Copyright 2017 Samsung Electronics Co., LTD
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

/***************************************************************************
 * JNI
 ***************************************************************************/


#include "util/gvr_jni.h"
#include "ovr_gear_controller.h"

namespace gvr {
    extern "C" {

    JNIEXPORT jlong JNICALL Java_org_gearvrf_OvrNativeGearController_ctor(JNIEnv *env,
                                                                          jobject obj, jobject
                                                                          jreadback_buffer);

    JNIEXPORT void JNICALL Java_org_gearvrf_OvrNativeGearController_delete(JNIEnv *env,
                                                                           jobject obj,
                                                                           jlong jController);

    JNIEXPORT jlong JNICALL Java_org_gearvrf_OvrNativeGearController_ctor(JNIEnv *env,
                                                                          jobject obj, jobject
                                                                          jreadback_buffer) {
        float *data = (float *) env->GetDirectBufferAddress(jreadback_buffer);
        return reinterpret_cast<jlong>(new GearController(data));
    }

    JNIEXPORT void JNICALL Java_org_gearvrf_NativeConfigurationManager_delete(JNIEnv *env,
                                                                              jobject obj,
                                                                              jlong
                                                                              jController) {
        delete reinterpret_cast<GearController *>(jController);
    }
    }
}
