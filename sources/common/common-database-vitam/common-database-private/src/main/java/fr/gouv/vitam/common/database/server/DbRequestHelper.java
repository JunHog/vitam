/**
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2019)
 * <p>
 * contact.vitam@culture.gouv.fr
 * <p>
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently.
 * <p>
 * This software is governed by the CeCILL 2.1 license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL 2.1 license as
 * circulated by CEA, CNRS and INRIA at the following URL "http://www.cecill.info".
 * <p>
 * As a counterpart to the access to the source code and rights to copy, modify and redistribute granted by the license,
 * users are provided only with a limited warranty and the software's author, the holder of the economic rights, and the
 * successive licensors have only limited liability.
 * <p>
 * In this respect, the user's attention is drawn to the risks associated with loading, using, modifying and/or
 * developing or reproducing the software by the user in light of its specific status of free software, that may mean
 * that it is complicated to manipulate, and that also therefore means that it is reserved for developers and
 * experienced professionals having in-depth computer knowledge. Users are therefore encouraged to load and test the
 * software's suitability as regards their requirements in conditions enabling the security of their systems and/or data
 * to be ensured and, more generally, to use and operate it in the same conditions as regards security.
 * <p>
 * The fact that you are presently reading this means that you have had knowledge of the CeCILL 2.1 license and that you
 * accept its terms.
 */
package fr.gouv.vitam.common.database.server;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import fr.gouv.vitam.common.exception.VitamDBException;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.parameter.ParameterHelper;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import org.bson.conversions.Bson;

import com.mongodb.ServerAddress;
import com.mongodb.ServerCursor;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;

import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.database.builder.query.NopQuery;
import fr.gouv.vitam.common.database.builder.request.exception.InvalidCreateOperationException;
import fr.gouv.vitam.common.database.collections.VitamCollection;
import fr.gouv.vitam.common.database.parser.request.single.RequestParserSingle;
import fr.gouv.vitam.common.database.server.mongodb.VitamDocument;
import fr.gouv.vitam.common.database.translators.mongodb.SelectToMongodb;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.logging.SysErrLogger;

/**
 * DbRequest Helper common for Single and Multiple
 */
public class DbRequestHelper {

    /**
     * VitamLogger
     */
    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(DbRequestHelper.class);
    public static final String
        CONSISTENCY_ERROR_THE_DOCUMENT_GUID_S_IN_ES_IS_NOT_IN_MONGO_DB_ANYMORE_TENANT_S_REQUEST_ID_S =
        "[Consistency Error] : The document guid=%s in ES is not in MongoDB anymore, tenant : %s, requestId : %s";

    /**
     * Empty
     */
    private DbRequestHelper() {
        // Empty
    }


    /**
     * Private method for select using MongoDb from Elasticsearch result
     *
     * @param collection
     * @param parser
     * @param list       list of Ids
     * @param scores     can be null, containing scores
     * @return MongoCursor<VitamDocument < ?>>
     * @throws InvalidParseOperationException  when query is not correct
     * @throws InvalidCreateOperationException
     */
    @SuppressWarnings("unchecked")
    public static MongoCursor<VitamDocument<?>> selectMongoDbExecuteThroughFakeMongoCursor(
        VitamCollection collection, RequestParserSingle parser, List<String> list, List<Float> scores)
        throws InvalidParseOperationException, VitamDBException {
        final SelectToMongodb selectToMongoDb = new SelectToMongodb(parser);
        final Bson projection = selectToMongoDb.getFinalProjection();
        final boolean isIdIncluded = selectToMongoDb.idWasInProjection();
        Bson initialCondition = Filters.in(VitamDocument.ID, list);
        if (!(parser.getRequest().getQuery() instanceof NopQuery)) {
            try {
                initialCondition = Filters.and(initialCondition, selectToMongoDb.getNthQueries(0));
            } catch (IllegalAccessError | IllegalAccessException e) {
                SysErrLogger.FAKE_LOGGER.ignoreLog(e);
            }
        }
        FindIterable<VitamDocument<?>> find =
            (FindIterable<VitamDocument<?>>) collection.getCollection().find(initialCondition);
        if (projection != null) {
            find = find.projection(projection);
        }
        // Build aggregate $project condition
        final int nb = list.size();
        final List<VitamDocument<?>> firstList = new ArrayList<>(nb);
        for (final String id : list) {
            firstList.add(new FakeVitamDocument());
        }
        ServerAddress serverAddress;
        int nbFinal = 0;
        try (MongoCursor<VitamDocument<?>> cursor = find.iterator()) {
            serverAddress = cursor.getServerAddress();
            while (cursor.hasNext()) {
                final VitamDocument<?> item = cursor.next();
                // do not use getId() because Logbook will not work
                final int rank = list.indexOf(item.getString(VitamDocument.ID));
                if (!isIdIncluded) {
                    item.remove(VitamDocument.ID);
                }
                firstList.set(rank, item);
                nbFinal++;
            }
        }
        final List<VitamDocument<?>> finalList = new ArrayList<>(nbFinal);
        if (VitamConfiguration.isExportScore() && scores != null
            && collection.isUseScore() && selectToMongoDb.isScoreIncluded()) {
            for (int i = 0; i < nb; i++) {
                VitamDocument<?> vitamDocument = firstList.get(i);
                if (!(vitamDocument instanceof FakeVitamDocument)) {
                    Float score = Float.valueOf(1);
                    try {
                        score = scores.get(i);
                        if (score.isNaN()) {
                            score = Float.valueOf(1);
                        }
                    } catch (IndexOutOfBoundsException e) {
                        SysErrLogger.FAKE_LOGGER.ignoreLog(e);
                    }
                    vitamDocument.append(VitamDocument.SCORE, score);
                    finalList.add(vitamDocument);
                }
            }
        } else {
            finalList.addAll(firstList);
        }

        // manage synchronization errors between elasticSearch and MongoDB.
        final List<VitamDocument<?>> finalListWithoutFakeDocs = new ArrayList<>(nbFinal);
        List<String> listDesynchronizedResults = new ArrayList<>();
        for (int i = 0; i < finalList.size(); i++) {
            if (!finalList.get(i).isEmpty()) {
                // delete fakeDocuments from desynchronized results
                finalListWithoutFakeDocs.add(finalList.get(i));
            } else {
                // get the identifiers of the desynchronized results
                listDesynchronizedResults.add(list.get(i));
            }
        }

        // log synchronization errors between elasticSearch and MongoDB.
        listDesynchronizedResults.forEach(
            x -> LOGGER.error(String.format(
                CONSISTENCY_ERROR_THE_DOCUMENT_GUID_S_IN_ES_IS_NOT_IN_MONGO_DB_ANYMORE_TENANT_S_REQUEST_ID_S, x,
                ParameterHelper.getTenantParameter(), VitamThreadUtils.getVitamSession().getRequestId())));

        // As soon as we detect a synchronization error MongoDB / ES, we return an error.
        if (!listDesynchronizedResults.isEmpty()) {
            throw new VitamDBException("[Consistency ERROR] : An internal data consistency error has been detected !");
        }

        firstList.clear();
        return new MongoCursor<VitamDocument<?>>() {
            int rank = 0;
            int max = finalListWithoutFakeDocs.size();
            ServerAddress finalServerAddress = serverAddress;
            List<VitamDocument<?>> list = finalListWithoutFakeDocs;

            @Override
            public void close() {
                // Nothing to do
            }

            @Override
            public boolean hasNext() {
                return rank < max;
            }

            @Override
            public VitamDocument<?> next() {
                if (rank >= max) {
                    throw new NoSuchElementException();
                }
                final VitamDocument<?> doc = list.get(rank);
                rank++;
                return doc;
            }

            @Override
            public VitamDocument<?> tryNext() {
                if (rank >= max) {
                    return null;
                }
                final VitamDocument<?> doc = list.get(rank);
                rank++;
                return doc;
            }

            @Override
            public ServerCursor getServerCursor() {
                return null;
            }

            @Override
            public ServerAddress getServerAddress() {
                return finalServerAddress;
            }
        };
    }

}
