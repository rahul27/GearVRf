package org.gearvrf.animation.keyframe;

import java.util.HashMap;
import java.util.Map;

import org.gearvrf.GVRSceneObject;
import org.gearvrf.GVRSkeletalController;
import org.gearvrf.Pose;
import org.gearvrf.Skeleton;

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
            pose.updateLocalMatrix(sceneObject, channel.animate(animationTick));
        }
        skeleton.update();
    }
}