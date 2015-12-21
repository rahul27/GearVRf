/* Copyright 2015 Samsung Electronics Co., LTD
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

package org.gearvrf.simplesample;

import static android.opengl.GLES20.GL_ARRAY_BUFFER;
import static android.opengl.GLES20.GL_ELEMENT_ARRAY_BUFFER;
import static android.opengl.GLES20.glBindBuffer;
import static android.opengl.GLES30.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import org.gearvrf.GVRAndroidResource;
import org.gearvrf.GVRCameraRig;
import org.gearvrf.GVRContext;
import org.gearvrf.GVRScene;
import org.gearvrf.GVRSceneObject;
import org.gearvrf.GVRScript;
import org.gearvrf.GVRTexture;
import org.gearvrf.utility.Log;

import android.content.Context;
import android.graphics.Color;


import android.opengl.GLES30;

public class SampleViewManager extends GVRScript {

    private GVRContext mGVRContext;
    private static final int FLOAT_SIZE_BYTES = Float.SIZE / Byte.SIZE;
    private static final int TRIANGLE_ATTRIBUTE_SIZE_POS = 3;
    private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = TRIANGLE_ATTRIBUTE_SIZE_POS
            * FLOAT_SIZE_BYTES;
    private static final int TOTAL_NUM_VERTICES = 4;
    private static final String TAG = SampleViewManager.class.getSimpleName();
    private Context context;
    private static final int DEFAULT_WIDTH = 160, DEFAULT_HEIGHT = 120;

    private float[] vertices = { -1.0f, -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, -1.0f,
            1.0f, -1.0f, 1.0f, 1.0f, -1.0f };

    private FloatBuffer vertexData;
    private int program;

    private int position;
    private int textureId;
    private ByteBuffer readbackBuffer;
    private GVRScene scene;
    private int[] savedFramebuffer = new int[1];
    int[] renderBuffer = new int[1];
    int[] fbo = new int[1];

    @Override
    public void onInit(GVRContext gvrContext) {

        // save context for possible use in onStep(), even though that's empty
        // in this sample
        mGVRContext = gvrContext;
        context = gvrContext.getContext();

        scene = gvrContext.getNextMainScene();

        // set background color
        GVRCameraRig mainCameraRig = scene.getMainCameraRig();
        mainCameraRig.getLeftCamera().setBackgroundColor(Color.WHITE);
        mainCameraRig.getRightCamera().setBackgroundColor(Color.WHITE);

        // load texture
        GVRTexture texture = gvrContext.loadTexture(
                new GVRAndroidResource(mGVRContext, R.drawable.gearvr_logo));

        // create a scene object (this constructor creates a rectangular scene
        // object that uses the standard 'unlit' shader)
        GVRSceneObject sceneObject = new GVRSceneObject(gvrContext, 4.0f, 2.0f,
                texture);

        // set the scene object position
        sceneObject.getTransform().setPosition(0.0f, 0.0f, -3.0f);

        // add the scene object to the scene graph
        scene.addSceneObject(sceneObject);

        // vertices
        vertexData = ByteBuffer
                .allocateDirect(vertices.length * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertexData.put(vertices).position(0);

        // TODO release shaders
        int vertexShader = loadGLShader(GL_VERTEX_SHADER,
                R.raw.glvshader);
        int fragmentShader = loadGLShader(GL_FRAGMENT_SHADER,
                R.raw.glfshader);

        program = glCreateProgram();
        checkGlError(TAG);
        glAttachShader(program, vertexShader);
        checkGlError(TAG);
        glAttachShader(program, fragmentShader);
        checkGlError(TAG);
        glLinkProgram(program);
        checkGlError(TAG);
        glUseProgram(program);
        checkGlError(TAG);
        position = glGetAttribLocation(program, "position");
        checkGlError(TAG);
        glViewport(0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        checkGlError(TAG);
        int requiredTextures = 1;
        int[] textureHandles = new int[requiredTextures];
        glGenTextures(requiredTextures, textureHandles, 0);
        glGenFramebuffers(1, fbo, 0);
        glGenRenderbuffers(1, renderBuffer, 0);
        checkGlError(TAG);
        textureId = textureHandles[0];
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, DEFAULT_WIDTH, DEFAULT_HEIGHT,
                0, GL_RGBA, GL_UNSIGNED_BYTE, null);

        checkGlError(TAG);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
       
        checkGlError(TAG);
        readbackBuffer = ByteBuffer
                .allocateDirect(DEFAULT_WIDTH * DEFAULT_HEIGHT * 4);
        readbackBuffer.order(ByteOrder.nativeOrder());

        // Create framebuffer
        checkGlError(TAG); 
        glBindRenderbuffer(GL_RENDERBUFFER, renderBuffer[0]);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT16,
                DEFAULT_WIDTH, DEFAULT_HEIGHT);

        glBindRenderbuffer(GL_RENDERBUFFER, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo[0]);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                GL_TEXTURE_2D, textureId, 0);

        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT,
                GL_RENDERBUFFER, renderBuffer[0]);

        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            Log.d(TAG, "Framebuffer error %d ", status);
        } else {
            Log.d(TAG, "Framebuffer fine");
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        checkGlError(TAG);
    }

    private int loadGLShader(int type, int resId) {
        String code = readRawTextFile(resId);
        int shader = glCreateShader(type);
        checkGlError(TAG);
        glShaderSource(shader, code);
        checkGlError(TAG);
        glCompileShader(shader);
        checkGlError(TAG);

        // Get the compilation status.
        final int[] compileStatus = new int[1];
        glGetShaderiv(shader, GL_COMPILE_STATUS, compileStatus, 0);
        checkGlError(TAG);

        // If the compilation failed, delete the shader.
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Error compiling shader: " + glGetShaderInfoLog(shader));
            glDeleteShader(shader);
            checkGlError(TAG);
            shader = 0;
        }

        if (shader == 0) {
            throw new RuntimeException("Error creating shader.");
        }

        return shader;
    }

    private String readRawTextFile(int resId) {
        InputStream inputStream = context.getResources().openRawResource(resId);
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public void onStep() {
        
        // These two lines fix the issue
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        
        glGetIntegerv(GL_FRAMEBUFFER_BINDING, savedFramebuffer, 0);
        checkGlError(TAG);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo[0]);
        glViewport(0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                GL_TEXTURE_2D, textureId, 0);

        GLES30.glReadBuffer(GL_COLOR_ATTACHMENT0);
        checkGlError(TAG);
        glDisable(GL_DEPTH_TEST);
        glDisable( GL_CULL_FACE );
        checkGlError(TAG);

        checkGlError(TAG);
        glActiveTexture(GL_TEXTURE0);
        checkGlError(TAG);
        glBindTexture(GL_TEXTURE_2D, textureId);
        checkGlError(TAG);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        checkGlError(TAG);
        glUseProgram(program);
        checkGlError(TAG);
        glEnableVertexAttribArray(position);

        checkGlError(TAG);
        vertexData.position(0);
        glVertexAttribPointer(position, 3, GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, vertexData);
        checkGlError(TAG);

        glDrawArrays(GL_TRIANGLE_STRIP, 0, TOTAL_NUM_VERTICES);
        checkGlError(TAG);
        glReadPixels(0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT, GL_RGBA,
                GL_UNSIGNED_BYTE, readbackBuffer);
        checkGlError(TAG);
        readbackBuffer.position(0);
        Log.d(TAG, "Values read are %d %d", readbackBuffer.get(),
                readbackBuffer.get());
        glDisableVertexAttribArray(position);
        checkGlError(TAG);
        glBindTexture(GL_TEXTURE_2D, 0);
        checkGlError(TAG);
        glUseProgram(0);
        checkGlError(TAG);
        glBindFramebuffer(GL_FRAMEBUFFER, savedFramebuffer[0]);
        checkGlError(TAG);
    }

    public static void checkGlError(String glOperation) {
        int error;
        while ((error = glGetError()) != GL_NO_ERROR) {
            Log.e(TAG, glOperation + ": glError " + error);
            throw new RuntimeException(glOperation + ": glError " + error);
        }
    }
}
