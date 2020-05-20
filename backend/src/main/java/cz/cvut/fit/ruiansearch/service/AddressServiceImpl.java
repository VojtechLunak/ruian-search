package cz.cvut.fit.ruiansearch.service;

import cz.cvut.fit.ruiansearch.model.Address;
import cz.cvut.fit.ruiansearch.repository.AddressRepository;
import lombok.Cleanup;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.geo.Distance;
import org.springframework.data.solr.core.SolrTemplate;
import org.springframework.data.solr.core.geo.Point;
import org.springframework.data.solr.core.query.*;
import org.springframework.data.solr.core.query.result.GroupPage;
import org.springframework.data.solr.core.query.result.GroupResult;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AddressServiceImpl implements AddressService {
    private final AddressRepository addressRepository;
    private final String collectionName = "ruian";

    public AddressServiceImpl(AddressRepository addressRepository) {
        this.addressRepository = addressRepository;
    }

    @Override
    public Page<Address> search(
            String city,
            String district,
            String street,
            String houseNumber,
            Pageable pageable) {
        return addressRepository.search(
                city, district, street, houseNumber, pageable);
    }

    @Override
    public Page<Address> searchWithEmptyStreet(
            String city,
            String district,
            String houseNumber,
            Pageable pageable) {
        return addressRepository.searchWithEmptyStreet(
                city, district, houseNumber, pageable);
    }

    @Override
    public Page<Address> findByAdmCodeStartsWith(String admCode, Pageable pageable) {
        return addressRepository.findByAdmCodeStartsWith(admCode, pageable);
    }

    @Override
    public Optional<Address> findByAdmCode(String admCode) {
        return addressRepository.findByAdmCodeEquals(admCode);
    }

    @Override
    public GroupResult<Address> getCitySuggestions(String city) {
        @Cleanup
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext("cz.cvut.fit.ruiansearch.config");
        SolrTemplate solrTemplate = (SolrTemplate) context.getBean("solrTemplate");
        
        Field field = new SimpleField("N_zev_obce");
        SimpleQuery query = new SimpleQuery(new SimpleStringCriteria("Obec:" + city));

        setGroupOptions(field, query);
        GroupPage<Address> page = solrTemplate.queryForGroupPage(collectionName, query, Address.class);

        return page.getGroupResult(field);
    }

    @Override
    public GroupResult<Address> getDistrictSuggestions(String city, String district) {
        @Cleanup
        AnnotationConfigApplicationContext context = 
                new AnnotationConfigApplicationContext("cz.cvut.fit.ruiansearch.config");
        SolrTemplate solrTemplate = (SolrTemplate) context.getBean("solrTemplate");

        Field field = new SimpleField("N_zev___sti_obce");
        SimpleQuery query = new SimpleQuery(new SimpleStringCriteria("Cast_obce:" + district));
        FilterQuery cityFilter = new SimpleFilterQuery(
                new Criteria("N_zev_obce").expression(wrapInQuotes(city)));

        setGroupOptions(field, query);
        query.addFilterQuery(cityFilter);
        GroupPage<Address> page = solrTemplate.queryForGroupPage(collectionName, query, Address.class);

        return page.getGroupResult(field);
    }

    @Override
    public GroupResult<Address> getStreetSuggestions(String city, String district, String street) {
        @Cleanup
        AnnotationConfigApplicationContext context = 
                new AnnotationConfigApplicationContext("cz.cvut.fit.ruiansearch.config");
        SolrTemplate solrTemplate = (SolrTemplate) context.getBean("solrTemplate");

        Field field = new SimpleField("N_zev_ulice");
        SimpleQuery query = new SimpleQuery(new SimpleStringCriteria("Ulice:" + street));
        FilterQuery cityFilter = new SimpleFilterQuery(
                new Criteria("N_zev_obce").expression(wrapInQuotes(city)));
        FilterQuery districtFilter = new SimpleFilterQuery(
                new Criteria("N_zev___sti_obce").expression(wrapInQuotes(district)));

        setGroupOptions(field, query);
        query.addFilterQuery(cityFilter)
            .addFilterQuery(districtFilter);
        GroupPage<Address> page = solrTemplate.queryForGroupPage(collectionName, query, Address.class);

        return page.getGroupResult(field);
    }

    @Override
    public GroupResult<Address> getHouseNumberSuggestions(
            String city,
            String district,
            String street,
            String houseNumber) {
        @Cleanup
        AnnotationConfigApplicationContext context = 
                new AnnotationConfigApplicationContext("cz.cvut.fit.ruiansearch.config");
        SolrTemplate solrTemplate = (SolrTemplate) context.getBean("solrTemplate");

        Field field = new SimpleField("Identifikace");

        Criteria queryCriteria = new Criteria("Identifikace_cs").is(houseNumber)
                .or("Cislo_orientacni").is(houseNumber);
        SimpleQuery query = new SimpleQuery(queryCriteria);
        FilterQuery cityFilter = new SimpleFilterQuery(
                new Criteria("N_zev_obce").expression(wrapInQuotes(city)));
        FilterQuery districtFilter = new SimpleFilterQuery(
                new Criteria("N_zev___sti_obce").expression(wrapInQuotes(district)));
        FilterQuery streetFilter = new SimpleFilterQuery(
                new Criteria("N_zev_ulice").expression(wrapInQuotes(street)));

        setGroupOptions(field, query);
        query.addFilterQuery(cityFilter)
            .addFilterQuery(districtFilter)
            .addFilterQuery(streetFilter);
        GroupPage<Address> page = solrTemplate.queryForGroupPage(collectionName, query, Address.class);

        return page.getGroupResult(field);
    }

    @Override
    public Page<Address> getNearbyAddresses(Point center, Double distance) {
        @Cleanup
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext("cz.cvut.fit.ruiansearch.config");
        SolrTemplate solrTemplate = (SolrTemplate) context.getBean("solrTemplate");

        StringBuffer queryCriteria = new StringBuffer();
        queryCriteria.append("{!geofilt pt=");
        queryCriteria.append(center.getX());
        queryCriteria.append(",");
        queryCriteria.append(center.getY());
        queryCriteria.append(" sfield=Coordinates_lat_lon d=");
        queryCriteria.append(distance);
        queryCriteria.append(" score=distance}");

        SimpleQuery query = new SimpleQuery(new SimpleStringCriteria(queryCriteria.toString()));
        query.addSort(Sort.by(Sort.Direction.ASC, "score"));
        Page<Address> page = solrTemplate.query(collectionName, query, Address.class);

        return page;
    }


    private void setGroupOptions(Field field, SimpleQuery query) {
        /*
            Set field used to group results.
            setLimit sets number of results for each group (value),
            since we only want the group value for suggestions, limit expression set to 0.
         */
        GroupOptions options = new GroupOptions()
                .addGroupByField(field)
                .setLimit(0);

        query.setGroupOptions(options);
    }

    /*
        Wraps string in quotes, unless it's a *.
        If * was wrapped in quotes, Solr would evaluate it as string, not a wildcard.
        Other strings need to be wrapped to be correctly evaluated by Solr,
        because they can contain multiple words.
     */
    private String wrapInQuotes(String input) {
        if (input.equals("*")) {
            return input;
        }

        StringBuilder output = new StringBuilder(input);
        output.append("\"");
        output.insert(0, "\"");

        return output.toString();
    }
}
