/*
 * Copyright (c) 2010-2020 Brookhaven National Laboratory
 * Copyright (c) 2010-2020 Helmholtz-Zentrum Berlin für Materialien und Energie GmbH
 * All rights reserved. Use is subject to license terms and conditions.
 */
package gov.bnl.olog;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.elasticsearch.action.DocWriteResponse.Result;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequest;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.engine.DocumentMissingException;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.repository.CrudRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import gov.bnl.olog.entity.Property;
import gov.bnl.olog.entity.State;


@Repository
public class PropertyRepository implements CrudRepository<Property, String>
{
    @Value("${elasticsearch.property.index:olog_properties}")
    private String ES_PROPERTY_INDEX;
    @Value("${elasticsearch.property.type:olog_property}")
    private String ES_PROPERTY_TYPE;

    @Value("${elasticsearch.result.size.properties:10}")
    private int propertiesResultSize;

    @Autowired
    @Qualifier("indexClient")
    RestHighLevelClient client;

    private static final ObjectMapper mapper = new ObjectMapper();

    private Logger logger = Logger.getLogger(PropertyRepository.class.getName());

    @Override
    public <S extends Property> S save(S property)
    {
        try
        {
            IndexRequest indexRequest = new IndexRequest(ES_PROPERTY_INDEX, ES_PROPERTY_TYPE, property.getName())
                    .source(mapper.writeValueAsBytes(property), XContentType.JSON);
            IndexResponse response = client.index(indexRequest, RequestOptions.DEFAULT);
            if (response.getResult().equals(Result.CREATED) ||
                response.getResult().equals(Result.UPDATED))
            {
                BytesReference ref = client.get(new GetRequest(ES_PROPERTY_INDEX, ES_PROPERTY_TYPE, response.getId()),
                        RequestOptions.DEFAULT).getSourceAsBytesRef();
                Property createdProperty = mapper.readValue(ref.streamInput(), Property.class);
                return (S) createdProperty;
            }
        } catch (Exception e)
        {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return null;
    }

    @Override
    public <S extends Property> Iterable<S> saveAll(Iterable<S> properties)
    {
        BulkRequest bulk = new BulkRequest();
        properties.forEach(property -> {
            try
            {
                bulk.add(new IndexRequest(ES_PROPERTY_INDEX, ES_PROPERTY_TYPE, property.getName())
                        .source(mapper.writeValueAsBytes(property), XContentType.JSON));
            } catch (JsonProcessingException e)
            {
                logger.log(Level.SEVERE, e.getMessage(), e);
            }
        });
        BulkResponse bulkResponse;
        try
        {
            bulk.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
            bulkResponse = client.bulk(bulk, RequestOptions.DEFAULT);

            if (bulkResponse.hasFailures())
            {
                // process failures by iterating through each bulk response item
                bulkResponse.forEach(response -> {
                    if (response.getFailure() != null)
                    {
                        logger.log(Level.SEVERE, response.getFailureMessage(),
                                response.getFailure().getCause());
                    }
                });
            } else
            {
                return properties;
            }
        } catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Optional<Property> findById(String propertyName)
    {
        try
        {
            GetResponse result = client.get(new GetRequest(ES_PROPERTY_INDEX, ES_PROPERTY_TYPE, propertyName),
                                                           RequestOptions.DEFAULT);
            if (result.isExists())
            {
                return Optional.of(mapper.readValue(result.getSourceAsBytesRef().streamInput(), Property.class));
            } else
            {
                return Optional.empty();
            }
        } catch (Exception e)
        {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public boolean existsById(String propertyName)
    {
        try
        {
            return client.exists(new GetRequest(ES_PROPERTY_INDEX, ES_PROPERTY_TYPE, propertyName), RequestOptions.DEFAULT);
        } catch (IOException e)
        {
            logger.log(Level.SEVERE, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to find property: " + propertyName, e);
        }
    }

    @Override
    public Iterable<Property> findAll()
    {
        return findAll(false);
    }

    public Iterable<Property> findAll(boolean includeInactive)
    {
        try
        { 

            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            if (!includeInactive)
            {
                sourceBuilder.query(QueryBuilders.termQuery("state", State.Active.toString()));
            }
            sourceBuilder.timeout(new TimeValue(10, TimeUnit.SECONDS));
            sourceBuilder.size(propertiesResultSize);

            SearchResponse response = client.search(
                    new SearchRequest(ES_PROPERTY_INDEX).types(ES_PROPERTY_TYPE).source(sourceBuilder), RequestOptions.DEFAULT);

            List<Property> result = new ArrayList<Property>();
            response.getHits().forEach(h -> {
                BytesReference b = h.getSourceRef();
                try
                {
                    result.add(mapper.readValue(b.streamInput(), Property.class));
                } catch (IOException e)
                {
                    logger.log(Level.SEVERE, e.getMessage(), e);
                }
            });
            return result;
        } catch (Exception e)
        {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return null;
    }
    
    @Override
    public Iterable<Property> findAllById(Iterable<String> propertyNames)
    {
        MultiGetRequest request = new MultiGetRequest();
        for (String propertyName : propertyNames)
        {
            request.add(new MultiGetRequest.Item(ES_PROPERTY_INDEX, ES_PROPERTY_TYPE, propertyName));
        }
        try
        {
            List<Property> foundProperties = new ArrayList<Property>();
            MultiGetResponse response = client.mget(request, RequestOptions.DEFAULT);
            for (MultiGetItemResponse multiGetItemResponse : response)
            {
                if (!multiGetItemResponse.isFailed())
                {
                    GetResponse res = multiGetItemResponse.getResponse();
                    StreamInput str = res.getSourceAsBytesRef().streamInput();
                    foundProperties.add(mapper.readValue(str, Property.class));
                }
            }
            return foundProperties;
        } catch (Exception e)
        {
            logger.log(Level.SEVERE, "Failed to find properties: " + propertyNames, e);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Failed to find properties: " + propertyNames, null);
        }
    }

    @Override
    public long count()
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void deleteById(String propertyName)
    {
        try
        {

            UpdateResponse response = client.update(
                    new UpdateRequest(ES_PROPERTY_INDEX, ES_PROPERTY_TYPE, propertyName)
                                        .doc(jsonBuilder().startObject().field("state", State.Inactive).endObject())
                                        .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE),
                    RequestOptions.DEFAULT);

            if (response.getResult().equals(Result.UPDATED))
            {
                BytesReference ref = client
                        .get(new GetRequest(ES_PROPERTY_INDEX, ES_PROPERTY_TYPE, response.getId()), RequestOptions.DEFAULT)
                        .getSourceAsBytesRef();
                Property deletedProperty = mapper.readValue(ref.streamInput(), Property.class);
                logger.log(Level.INFO, "Deleted property " + deletedProperty.toLogger());
            }
        } catch (DocumentMissingException e)
        {
            logger.log(Level.SEVERE, propertyName + " Does not exist and thus cannot be deleted");
        } catch (Exception e)
        {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    public void deleteAttribute(String propertyName, String attributeName)
    {
        try
        {
            Property property = findById(propertyName).get();
            if (property != null)
            {
                property.setAttributes(property.getAttributes().stream().map(p -> {
                    if (p.getName().equals(attributeName))
                    {
                        p.setState(State.Inactive);
                    }
                    return p;
                }).collect(Collectors.toSet()));
            }


            UpdateResponse response = client.update(
                    new UpdateRequest(ES_PROPERTY_INDEX, ES_PROPERTY_TYPE, propertyName)
                            .doc(mapper.writeValueAsBytes(property), XContentType.JSON)
                            .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE),
                    RequestOptions.DEFAULT);

            if (response.getResult().equals(Result.UPDATED))
            {
                BytesReference ref = client
                        .get(new GetRequest(ES_PROPERTY_INDEX, ES_PROPERTY_TYPE, response.getId()), RequestOptions.DEFAULT)
                        .getSourceAsBytesRef();
                Property deletedProperty = mapper.readValue(ref.streamInput(), Property.class);
                logger.log(Level.INFO, "Deleted property attribute" + deletedProperty.toLogger());
            }
        } catch (DocumentMissingException e)
        {
            logger.log(Level.SEVERE, propertyName + " Does not exist and thus cannot be deleted");
        } catch (Exception e)
        {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    @Override
    public void delete(Property property)
    {
        deleteById(property.getName());
    }

    @Override
    public void deleteAll(Iterable<? extends Property> properties)
    {
        properties.forEach(property -> deleteById(property.getName()));
    }

    @Override
    public void deleteAll()
    {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Deleteting all properties not allowed");
    }

}
