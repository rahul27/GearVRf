package org.gearvrf.io.cursor3d;

import org.gearvrf.GVRComponent;
import org.gearvrf.GVRContext;
import org.gearvrf.GVRSceneObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SelectableGroup extends GVRComponent {
    static private long TYPE_SELECTABLE_GROUP = ((long) SelectableGroup.class.hashCode() << 32) &
            (System.currentTimeMillis() & 0xffffffff);
    private GVRSceneObject parent;
     Set<GVRSceneObject> intersectingList = new HashSet<GVRSceneObject>();

    protected SelectableGroup(GVRContext gvrContext, GVRSceneObject sceneObject) {
        super(gvrContext, 0);
        this.parent = sceneObject;
        mType = TYPE_SELECTABLE_GROUP;
    }

    @Override
    public void onAttach(GVRSceneObject newOwner) {
        super.onAttach(newOwner);
    }

    @Override
    public void onDetach(GVRSceneObject oldOwner) {
        super.onDetach(oldOwner);
    }

    @Override
    public long getType() {
        return super.getType();
    }

    public GVRSceneObject getParent() {
        return parent;
    }

    public static long getComponentType() {
        return TYPE_SELECTABLE_GROUP;
    }


}
