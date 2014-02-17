
var assert = chai.assert;

describe('BusStopLayerPlugin', function(){
    var dataOneBusStopType = ["2"];
    var dataTwoBusStopType = ["2","3"];
    var dataEmptyBusStopType = [];
    
    describe('#makePopupContent()', function() {
        var pluginInstance = null;
        var testOneBusStopTypeHtml =  '<img src="api/images/2">';
        var testTwoBusStopTypeHtml =  '<img src="api/images/2"><img src="api/images/3">';
        var testEmptyBusStopTypeHtml =  '';

        before(function(){
            pluginInstance = Oskari.clazz.create('Oskari.digiroad2.bundle.mapbusstop.plugin.BusStopLayerPlugin');
            pluginInstance._initTemplates();
        });

        it('should return one bus stop html by image tag', function () {
            assert.equal(testOneBusStopTypeHtml, pluginInstance._makePopupContent(dataOneBusStopType));
        });

        it('should return two various bus stop html by image tags', function () {
            assert.equal(testTwoBusStopTypeHtml, pluginInstance._makePopupContent(dataTwoBusStopType));
        });

        it('should return empty html', function () {
            assert.equal(testEmptyBusStopTypeHtml, pluginInstance._makePopupContent(dataEmptyBusStopType));
        });
    });

    describe('#getIconImages()', function(){
        var pluginInstance = null;
        var oneIconImageHtml =
            '<div class="callout">' +
                '<img src="api/images/2.png">' +
                '<div class="arrow-container">' +
                '<div class="arrow"></div>' +
                '</div>' +
                '<div class="dropHandle">' +
                '</div></div>';
        var twoIconImageHtml =
            '<div class="callout">' +
                '<img src="api/images/2.png">' +
                '<img src="api/images/3.png">' +
                '<div class="arrow-container">' +
                '<div class="arrow"></div>' +
                '</div>' +
                '<div class="dropHandle">' +
                '</div></div>';
        var noIconImageHtml =
            '<div class="callout">' +
                '<div class="arrow-container">' +
                '<div class="arrow"></div>' +
                '</div>' +
                '<div class="dropHandle">' +
                '</div></div>';        
        
        before(function(){
            pluginInstance = Oskari.clazz.create('Oskari.digiroad2.bundle.mapbusstop.plugin.BusStopLayerPlugin');
            pluginInstance._initTemplates();
        });        
        
        it('should return one bus stop html by image tag', function(){
            assert.equal(oneIconImageHtml,pluginInstance._getIconImages(dataOneBusStopType).outerHTML);
        });

        it('should return two bus stop html by images tag', function(){
            assert.equal(twoIconImageHtml,pluginInstance._getIconImages(dataTwoBusStopType).outerHTML);
        });

        it('should return html without images', function(){
            assert.equal(noIconImageHtml,pluginInstance._getIconImages(dataEmptyBusStopType).outerHTML);
        });
    });    
    
    describe('when adding a new bus stop', function() {
        var pluginInstance = null;
        var requests = [];
        var attributesCollectedCallback = null;
        var collectionCancelledCallback = null;
        var assetCreationData = [];
        var assetPropertyInsertions = [];
        var attributeCollectionRequest = {};
        var attributeShowRequest = {};
        var showInfoBoxRequest = {};
        var requestedInfoBoxType = '';
        var requestedInfoBoxTitle = '';
        var addedFeature = {};
        var destroyedFeature = {};
        var addedMarker = {};
        var attributeCollectionRequestBuilder = function(collectedCallback, cancellationCallback) {
            attributesCollectedCallback = collectedCallback;
            collectionCancelledCallback = cancellationCallback;
            return attributeCollectionRequest;
        };
        var showInfoBoxRequestBuilder = function(infoBoxType, infoBoxTitle) {
            requestedInfoBoxType = infoBoxType;
            requestedInfoBoxTitle = infoBoxTitle;
            return showInfoBoxRequest;
        };
        var attributeShowRequestBuilder = function() {
            return attributeShowRequest;
        };

        before(function() {
            pluginInstance = Oskari.clazz.create('Oskari.digiroad2.bundle.mapbusstop.plugin.BusStopLayerPlugin', {
                backend: _.extend({}, window.Backend, {
                    putAsset: function(data, success) {
                        assetCreationData.push(_.extend({}, data, { imageIds: [] }));
                        success( _.extend({}, data, { id: 123 }) );
                    },
                    putAssetPropertyValue: function(assetId, propertyId, data) {
                        assetPropertyInsertions.push({
                            assetId: assetId,
                            propertyId: propertyId,
                            data: data
                        });
                    },
                    getAsset: function(id, success) {
                        success( assetCreationData[0] );
                    }
                }),
                geometryCalculations: {
                    findNearestLine: function() {
                        return { roadLinkId: 5 };
                    },
                    getLineDirectionDegAngle: function() {
                        return 95;
                    }
                },
                layers: {
                    busstoplayer_235: [{}, {
                        addFeatures: function(feature) { addedFeature = feature; },
                        destroyFeatures: function(feature) { destroyedFeature = feature; }
                    }, {
                        addMarker: function(marker) { addedMarker = marker; }
                    }]
                }
            });
            pluginInstance._initTemplates();
            pluginInstance.setMapModule({
                getName: function() { return 'MapModule'; },
                getMap: function() { return {}; }
            });
            pluginInstance.startPlugin({
                register: function() {},
                registerForEventByName: function() {},
                getRequestBuilder: function(request) {
                    if (request === 'FeatureAttributes.CollectFeatureAttributesRequest') {
                        return attributeCollectionRequestBuilder;
                    } else if (request === 'InfoBox.ShowInfoBoxRequest') {
                        return showInfoBoxRequestBuilder;
                    } else if (request === 'FeatureAttributes.ShowFeatureAttributesRequest') {
                        return attributeShowRequestBuilder;
                    }
                    return null;
                },
                request: function(name, r) { requests.push(r); }
            });
            pluginInstance._addBusStopEvent({
                getLonLat: function () {
                    return {
                        lon: 30.5,
                        lat: 41.2
                    };
                }
            });
        });

        it('should request collection of feature attributes', function() {
            assert.equal(attributeCollectionRequest, requests[0]);
        });

        it('should request bus stop infobox', function() {
            assert.equal(showInfoBoxRequest, requests[1]);
            assert.equal('busStop', requestedInfoBoxType);
            assert.equal('Uusi Pysäkki', requestedInfoBoxTitle);
        });

        it('should add direction arrow feature to direction arrow layer', function() {
            assert.equal(addedFeature.style.externalGraphic, 'src/resources/digiroad2/bundle/mapbusstop/images/suuntain.png');
        });

        describe('and when feature attributes have been collected', function () {
            before(function() {
                assetCreationData = [];
                assetPropertyInsertions = [];
                requests = [];
                attributesCollectedCallback([
                    { propertyId: '5', propertyValues: [ { propertyValue:0, propertyDisplayValue:'textValue' } ] },
                    { propertyId: '1', propertyValues: [ { propertyValue:2, propertyDisplayValue:'' } ] }
                ]);
            });

            it('should create asset in back end', function () {
                assert.equal(1, assetCreationData.length);
                assert.deepEqual({ assetTypeId: 10, lon: 30.5, lat: 41.2, roadLinkId: 5, bearing: 95, imageIds: [] }, assetCreationData[0]);
            });

            it('should add asset properties to back end', function() {
                assert.equal(2, assetPropertyInsertions.length);
                assert.deepEqual({ assetId: 123, propertyId: '5', data: [ { propertyValue:0, propertyDisplayValue:'textValue' } ] }, assetPropertyInsertions[0]);
                assert.deepEqual({ assetId: 123, propertyId: '1', data: [ { propertyValue:2, propertyDisplayValue:'' } ] }, assetPropertyInsertions[1]);
            });

            it('should remove direction arrow feature from direction arrow layer', function() {
                assert.equal(destroyedFeature.style.externalGraphic, 'src/resources/digiroad2/bundle/mapbusstop/images/suuntain.png');
            });

            xit('should show bus stop marker on marker layer', function() {
                assert.equal(addedMarker.id, 123);
            });

            it('should request bus stop infobox', function() {
                assert.equal(showInfoBoxRequest, requests[0]);
                assert.equal('busStop', requestedInfoBoxType);
            });

            it('should request show of feature attributes', function() {
                assert.equal(attributeShowRequest, requests[1]);
            });
        });

        describe('and when feature attribute collection has been cancelled', function() {
            before(function() {
                destroyedFeature = {};
                collectionCancelledCallback();
            });

            it('should remove direction arrow feature from direction arrow layer', function() {
                assert.equal(destroyedFeature.style.externalGraphic, 'src/resources/digiroad2/bundle/mapbusstop/images/suuntain.png');
            });
        });
    });
});