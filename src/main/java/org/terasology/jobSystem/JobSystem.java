/*
 * Copyright 2013 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.jobSystem;

import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.ComponentSystem;
import org.terasology.entitySystem.systems.In;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.entitySystem.systems.UpdateSubscriberSystem;
import org.terasology.logic.location.LocationComponent;
import org.terasology.math.Vector3i;
import org.terasology.minion.path.MinionPathComponent;
import org.terasology.minion.path.MoveToEvent;
import org.terasology.minion.path.MovingPathFinishedEvent;
import org.terasology.pathfinding.componentSystem.PathReadyEvent;
import org.terasology.pathfinding.componentSystem.PathfinderSystem;
import org.terasology.pathfinding.model.Path;
import org.terasology.pathfinding.model.WalkableBlock;

import javax.vecmath.Vector3f;
import java.util.List;

/**
 * Assigns free jobs to minions
 *
 * @author synopia
 */
@RegisterSystem
public class JobSystem implements ComponentSystem, UpdateSubscriberSystem {
    private static final Logger logger = LoggerFactory.getLogger(JobSystem.class);

    @In
    private PathfinderSystem pathfinderSystem;
    @In
    private EntityManager entityManager;
    @In
    private JobBoard jobBoard;

    @Override
    public void update(float delta) {
        requestPaths();
    }

    @ReceiveEvent(components = {JobMinionComponent.class})
    public void onMovingPathFinished(MovingPathFinishedEvent event, EntityRef minion) {
        logger.info("Finished moving along " + event.getPathId());
        JobMinionComponent job = minion.getComponent(JobMinionComponent.class);
        JobPossibility chosenPossibility = job.chosenPossibility;
        job.chosenPossibility = null;
        job.state = JobMinionComponent.JobMinionState.UNASSIGNED;
        minion.saveComponent(job);

        EntityRef block = chosenPossibility.targetEntity;
        if (block != null) {
            JobBlockComponent jobBlock = block.getComponent(JobBlockComponent.class);
            jobBlock.assignedMinion = null;
            block.saveComponent(jobBlock);
            if (jobBlock.canMinionWork(block, minion)) {
                logger.info("Reached target, remove job");
                jobBlock.letMinionWork(block, minion);

                jobBoard.removeJob(block);
            }
        } else {
            chosenPossibility.job.letMinionWork(null, minion);
        }

    }

    @ReceiveEvent(components = {JobMinionComponent.class})
    public void onPathReady(final PathReadyEvent event, EntityRef minion) {
        JobMinionComponent minionComponent = minion.getComponent(JobMinionComponent.class);
        if (minionComponent.state != JobMinionComponent.JobMinionState.PATHS_REQUESTED) {
            return;
        }
        List<Path> allPaths = event.getPath();

        if (allPaths != null) {
            logger.info(allPaths.size() + " paths (" + event.getPathId() + ") ready for " + minion);
            Path bestPath = null;
            int minLen = Integer.MAX_VALUE;
            int index = 0;
            for (int i = 0; i < allPaths.size(); i++) {
                Path path = allPaths.get(i);
                if (path == Path.INVALID) {
                    continue;
                }
                if (path.size() < minLen) {
                    bestPath = path;
                    minLen = path.size();
                    index = i;
                }
            }
            if (bestPath != null) {
                logger.info("Path (len=" + bestPath.size() + ") assigned to " + minion);

                minionComponent.state = JobMinionComponent.JobMinionState.ASSIGNED;
                minionComponent.chosenPossibility = minionComponent.possibilities.get(index);
                minion.saveComponent(minionComponent);

                minion.send(new MoveToEvent(bestPath.getStart().getBlockPosition(), bestPath.getTarget().getBlockPosition()));
                return;
            }
        }
        logger.info("Paths invalidated for " + minion);
        minionComponent.state = JobMinionComponent.JobMinionState.UNASSIGNED;
        minion.saveComponent(minionComponent);
    }

    private void requestPaths() {
        for (EntityRef entity : entityManager.getEntitiesWith(JobMinionComponent.class, MinionPathComponent.class)) {
            JobMinionComponent job = entity.getComponent(JobMinionComponent.class);
            if (job.state == JobMinionComponent.JobMinionState.UNASSIGNED) {
                Vector3f worldPosition = entity.getComponent(LocationComponent.class).getWorldPosition();
                WalkableBlock block = pathfinderSystem.getBlock(worldPosition);
                if (block != null) {
                    List<JobPossibility> possibilities = jobBoard.findJobTargets(entity);

                    if (possibilities.size() > 0) {
                        job.state = JobMinionComponent.JobMinionState.PATHS_REQUESTED;
                        job.possibilities = possibilities;
                        entity.saveComponent(job);

                        logger.info("Requesting " + possibilities.size() + " paths for " + entity);
                        List<Vector3i> targetPositions = Lists.newArrayList();
                        for (JobPossibility possibility : possibilities) {
                            targetPositions.add(possibility.targetPos);
                        }
                        pathfinderSystem.requestPath(entity, block.getBlockPosition(), targetPositions);
                    }
                }
            }
        }
    }

    @Override
    public void initialise() {
        logger.info("Initialize JobSystem");
    }

    @Override
    public void shutdown() {
    }
}