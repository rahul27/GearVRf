package org.gearvrf.io.cursor3d;

import org.gearvrf.GVRComponent;
import org.gearvrf.GVRContext;
import org.gearvrf.GVRSceneObject;

public class SelectableGroup extends GVRComponent {
    static private long TYPE_SELECTABLE_GROUP = ((long) SelectableGroup.class.hashCode() << 32) &
            (System.currentTimeMillis() & 0xffffffff);
    private GVRSceneObject parent;

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
