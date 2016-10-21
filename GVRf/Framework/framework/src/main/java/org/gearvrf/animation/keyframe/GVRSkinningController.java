package org.gearvrf.animation.keyframe;

import java.util.HashMap;
import java.util.Map;

import org.gearvrf.GVRSceneObject;
import org.gearvrf.GVRSkeletalController;

/**
 * Controls skeletal animation (skinning).
 */
public class GVRSkinningController extends GVRAnimationController {
    private GVRSkeletalController controller;
    private Map<Integer, GVRSceneObject> channelToNode;

    public GVRSkinningController(GVRSceneObject sceneRoot, GVRKeyFrameAnimation animation) {
        super(animation);
        controller = new GVRSkeletalController(sceneRoot);
        channelToNode = new HashMap<>();
        parseTree(controller, sceneRoot);
        controller.pruneTree();
    }

    void parseTree(GVRSkeletalController controller, GVRSceneObject sceneObject) {
        int channelId = animation.findChannel(sceneObject.getName());
        if (channelId != -1) {
            channelToNode.put(channelId, sceneObject);
        } else {
            controller.setInvalid(sceneObject);
        }
        for (GVRSceneObject child : sceneObject.getChildren()) {
            parseTree(controller, child);
        }
    }

    @Override
    protected void animateImpl(float animationTick) {
        int i = 0;
        for (GVRAnimationChannel channel : animation.mChannels) {
            GVRSceneObject sceneObject = channelToNode.get(i++);
            controller.updateLocalMatrix(sceneObject, channel.animate(animationTick));
        }
        controller.update();
    }
}