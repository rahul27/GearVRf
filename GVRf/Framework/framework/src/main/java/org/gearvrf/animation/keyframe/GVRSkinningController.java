package org.gearvrf.animation.keyframe;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

import org.gearvrf.GVRSceneObject;
import org.gearvrf.GVRSkeletalController;
import org.gearvrf.GVRTransform;
import org.gearvrf.Pose;
import org.gearvrf.Skeleton;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Controls skeletal animation (skinning).
 */
public class GVRSkinningController extends GVRAnimationController {
    //private GVRSkeletalController controller;

    Skeleton skeleton;
    Pose pose;
    private Map<Integer, GVRSceneObject> channelToNode;

    public GVRSkinningController(GVRSceneObject sceneRoot, GVRKeyFrameAnimation animation) {
        super(animation);
        skeleton = new Skeleton(sceneRoot);
        skeleton.findBones(null);

        pose = skeleton.getPose();
        channelToNode = new HashMap<>();
        parseTree(pose, sceneRoot);
        pose.pruneTree();
    }

    void parseTree(Pose pose, GVRSceneObject sceneObject) {
        int channelId = animation.findChannel(sceneObject.getName());
        if (channelId != -1) {
            channelToNode.put(channelId, sceneObject);
            skeleton.setBoneOptions(sceneObject, Skeleton.BONE_ANIMATE);
        } else {
            pose.setInvalid(sceneObject);
        }
        for (GVRSceneObject child : sceneObject.getChildren()) {
            parseTree(pose, child);
        }
    }

    @Override
    protected void animateImpl(float animationTick) {
        int i = 0;
        for (GVRAnimationChannel channel : animation.mChannels) {
            GVRSceneObject sceneObject = channelToNode.get(i++);
            Matrix4f matrix4f = channel.animate(animationTick);
            sceneObject.getTransform().setModelMatrix(matrix4f);
        }
        skeleton.update();
    }
}