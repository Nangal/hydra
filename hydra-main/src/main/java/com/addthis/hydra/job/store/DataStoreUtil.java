/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.addthis.hydra.job.store;

import java.util.Arrays;
import java.util.List;

import com.addthis.basis.util.Parameter;

import com.addthis.hydra.query.AliasBiMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.addthis.hydra.job.store.SpawnDataStoreKeys.SPAWN_BALANCE_PARAM_PATH;
import static com.addthis.hydra.job.store.SpawnDataStoreKeys.SPAWN_COMMON_ALERT_PATH;
import static com.addthis.hydra.job.store.SpawnDataStoreKeys.SPAWN_COMMON_COMMAND_PATH;
import static com.addthis.hydra.job.store.SpawnDataStoreKeys.SPAWN_COMMON_MACRO_PATH;
import static com.addthis.hydra.job.store.SpawnDataStoreKeys.SPAWN_JOB_CONFIG_PATH;
import static com.addthis.hydra.job.store.SpawnDataStoreKeys.SPAWN_QUEUE_PATH;

/**
 * A class providing various utility functions for common data storage between hydra components
 */
public class DataStoreUtil {

    /* A list of datastore paths with values that should be cutover */
    private static final List<String> pathsToImport = Arrays.asList(SPAWN_QUEUE_PATH, SPAWN_BALANCE_PARAM_PATH);
    /* A list of datastore parent nodes with children that should be cutover */
    private static final List<String> parentsToImport = Arrays.asList(SPAWN_COMMON_COMMAND_PATH, SPAWN_COMMON_MACRO_PATH, SPAWN_JOB_CONFIG_PATH, AliasBiMap.ALIAS_PATH, SPAWN_COMMON_ALERT_PATH);
    /* A list of nodes beneath each job node */
    private static final List<String> jobParametersToImport = Arrays.asList("config", "queryconfig", "tasks", "alerts");

    private static final Logger log = LoggerFactory.getLogger(DataStoreUtil.class);

    private static final String canonicalDataStoreType = Parameter.value("spawn.jdbc.store.type", "ZK");
    private static final String h2DbPath = Parameter.value("spawn.jdbc.db.path", "etc/spawndatastore");
    private static final String tableName = Parameter.value("spawn.table.name", "datastoretable");
    private static final String mysqlDbName = Parameter.value("spawn.mysql.db.name", "spawndatastore");
    private static final String mysqlHostName = Parameter.value("spawn.mysql.host", "localhost");
    private static final int mysqlPort = Parameter.intValue("spawn.mysql.port", 3306);

    public static enum DataStoreType {ZK, MYSQL, H2}

    /**
     * Create the canonical SpawnDataStore based on the system parameters
     *
     * @return A SpawnDataStore of the appropriate implementation
     */
    public static SpawnDataStore makeCanonicalSpawnDataStore() throws Exception {
        return makeSpawnDataStore(DataStoreType.valueOf(canonicalDataStoreType));
    }

    public static SpawnDataStore makeSpawnDataStore(DataStoreType type) throws Exception {
        switch (type) {
            case ZK: return new ZookeeperDataStore(null);
            case MYSQL: return new MysqlDataStore(mysqlHostName, mysqlPort, mysqlDbName, tableName);
            case H2: return new H2DataStore(h2DbPath, tableName);
            default: throw new IllegalArgumentException("Unexpected DataStoreType " + type);
        }
    }

    /**
     * A method to cut over all necessary data from on DataStore to another. Placeholder for now until more data stores are implemented.
     *
     * @param sourceDataStore The old datastore to read from
     * @param targetDataStore The new datastore to push data to
     * @throws Exception If any part of the cutover fails
     */
    public static void cutoverBetweenDataStore(SpawnDataStore sourceDataStore, SpawnDataStore targetDataStore) throws Exception {
        log.warn("Beginning cutover from " + sourceDataStore.getDescription() + " to " + targetDataStore.getDescription());
        for (String value : pathsToImport) {
            importValue(value, sourceDataStore, targetDataStore);
        }
        for (String parent : parentsToImport) {
            importParentAndChildren(parent, sourceDataStore, targetDataStore);
        }
        List<String> jobIds = sourceDataStore.getChildrenNames(SPAWN_JOB_CONFIG_PATH);
        if (jobIds != null) {
            for (String jobId : jobIds) {
                if (jobId == null) {
                    continue;
                }
                log.warn("Cutting over job data for " + jobId);
                String basePath = SPAWN_JOB_CONFIG_PATH + "/" + jobId;
                importValue(basePath, sourceDataStore, targetDataStore);
                for (String parameter : jobParametersToImport) {
                    importValue(basePath + "/" + parameter, sourceDataStore, targetDataStore);
                }
            }
        }
        log.warn("Finished cutover from " + sourceDataStore.getDescription() + " to " + targetDataStore.getDescription());
    }

    /**
     * Internal function to import the value of a row from one datastore to another
     *
     * @param path            The path to import
     * @param sourceDataStore The source to read from
     * @param targetDataStore The target to write to
     * @throws Exception If there is a problem during the transfer
     */
    private static void importValue(String path, SpawnDataStore sourceDataStore, SpawnDataStore targetDataStore) throws Exception {
        log.warn("Cutting over value of path " + path);
        String sourceValue = sourceDataStore.get(path);
        if (sourceValue != null) {
            targetDataStore.put(path, sourceValue);
        }
    }

    /**
     * Internal function to important all children beneath a certain parent path from one datastore to another
     *
     * @param parent          The parent id to grab the children from
     * @param sourceDataStore The source to read from
     * @param targetDataStore The target to write to
     * @throws Exception If there is a problem during the transfer
     */
    private static void importParentAndChildren(String parent, SpawnDataStore sourceDataStore, SpawnDataStore targetDataStore) throws Exception {
        log.warn("Cutting over children of path " + parent);
        importValue(parent, sourceDataStore, targetDataStore);
        List<String> children = sourceDataStore.getChildrenNames(parent);
        if (children != null) {
            for (String child : children) {
                targetDataStore.putAsChild(parent, child, sourceDataStore.getChild(parent, child));
            }
        }
    }
}
