/*
 * Copyright 2014 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.pathfinding.model;

import org.terasology.navgraph.WalkableBlock;

import java.util.ArrayList;

/**
 * @author synopia
 */
public class Path extends ArrayList<WalkableBlock> {
    public static final Path INVALID = new Path();

    public WalkableBlock getTarget() {
        if (size() == 0) {
            return null;
        }
        return get(size() - 1);
    }

    public WalkableBlock getStart() {
        if (size() == 0) {
            return null;
        }
        return get(0);
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        for (WalkableBlock block : this) {
            sb.append(block.getBlockPosition().toString());
            sb.append("->");
        }
        return sb.toString();
    }
}
