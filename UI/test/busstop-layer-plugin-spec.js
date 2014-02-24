
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
        var assetPosition = null;
        var assetCreationData = [];
        var attributeCollectionRequest = { name: 'attributeCollectionRequest' };
        var attributeShowRequest = { name: 'attributeShowRequest' };
        var showInfoBoxRequest = { name: 'showInfoBoxRequest' };
        var hideInfoBoxRequest = { name: 'hideInfoBoxRequest' };
        var requestedInfoBoxType = '';
        var requestedInfoBoxTitle = '';
        var hiddenInfoBoxId = '';
        var addedFeature = {};
        var destroyedFeature = {};
        var addedMarker = {};
        var attributeCollectionRequestBuilder = function(position, collectedCallback, cancellationCallback) {
            assetPosition = position;
            attributesCollectedCallback = collectedCallback;
            collectionCancelledCallback = cancellationCallback;
            return _.clone(attributeCollectionRequest);
        };
        var showInfoBoxRequestBuilder = function(infoBoxType, infoBoxTitle) {
            requestedInfoBoxType = infoBoxType;
            requestedInfoBoxTitle = infoBoxTitle;
            return _.clone(showInfoBoxRequest);
        };
        var hideInfoBoxRequestBuilder = function (infoBoxId) {
            hiddenInfoBoxId = infoBoxId;
            return _.clone(hideInfoBoxRequest);
        };
        var attributeShowRequestBuilder = function() {
            return _.clone(attributeShowRequest);
        };

        before(function() {
            pluginInstance = Oskari.clazz.create('Oskari.digiroad2.bundle.mapbusstop.plugin.BusStopLayerPlugin', {
                backend: _.extend({}, window.Backend, {
                    createAsset: function(data, success) {
                        assetCreationData.push(_.extend({}, data, { imageIds: [] }));
                        success( _.extend({}, assetCreationData[0], { id: 123, validityPeriod: 'current'}));
                    },
                    getAsset: function(id, success) {
                        success(_.extend({}, assetCreationData[0], { validityPeriod: 'current' }) );
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
                    road: {features: null},
                    asset: {
                      addMarker: function(marker) { addedMarker = marker; }
                    },
                    assetDirection: {
                        addFeatures: function(feature) { addedFeature = feature; },
                        destroyFeatures: function(feature) { destroyedFeature = feature; }
                    }
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
                    } else if (request === 'InfoBox.HideInfoBoxRequest') {
                        return hideInfoBoxRequestBuilder;
                    } else if (request === 'FeatureAttributes.ShowFeatureAttributesRequest') {
                        return attributeShowRequestBuilder;
                    }
                    return null;
                },
                request: function(name, r) { requests.push(r); },
                sentEvent: null,
                notifyAll: function(event) {
                    this.sentEvent = event;
                },
                getEventBuilder: function(event) {
                    return function(parameter) {
                        return {
                            name: event,
                            parameter: parameter
                        };
                    };
                }
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
            assert.deepEqual(attributeCollectionRequest, requests[0]);
        });

        it('should request bus stop infobox', function() {
            assert.deepEqual(showInfoBoxRequest, requests[1]);
            assert.equal('busStop', requestedInfoBoxType);
            assert.equal('Uusi Pysäkki', requestedInfoBoxTitle);
        });

        it('should add direction arrow feature to direction arrow layer', function() {
            assert.equal(addedFeature.style.externalGraphic, 'src/resources/digiroad2/bundle/mapbusstop/images/suuntain.png');
        });

        describe('and when validity direction is changed', function() {
            before(function() {
                addedFeature = {};
                destroyedFeature = {};
                pluginInstance.onEvent({
                    getName: function() { return 'featureattributes.FeatureAttributeChangedEvent'; },
                    getParameter: function() {
                        return {
                            propertyData: [{
                                propertyId: 'validityDirection',
                                values: [{
                                    propertyValue: 3
                                }]
                            }]
                        };
                    }
                });
            });

            it('should recreate direction arrow', function() {
                assert.equal(addedFeature.style.externalGraphic, 'src/resources/digiroad2/bundle/mapbusstop/images/suuntain.png');
                assert.equal(destroyedFeature.style.externalGraphic, 'src/resources/digiroad2/bundle/mapbusstop/images/suuntain.png');
            });
        });

        describe('and when feature attributes have been collected', function () {
            before(function() {
                assetCreationData = [];
                requests = [];
                attributesCollectedCallback([
                    { propertyId: '5', propertyValues: [ { propertyValue:0, propertyDisplayValue:'textValue' } ] },
                    { propertyId: '1', propertyValues: [ { propertyValue:2, propertyDisplayValue:'' } ] }
                ]);
            });

            it('should create asset in back end', function () {
                assert.equal(1, assetCreationData.length);
                var expectedProperties = [{id: "5",
                                           values: [{propertyValue: 0,
                                                     propertyDisplayValue: "textValue"}]},
                                          {id: "1",
                                           values: [{propertyValue: 2,
                                                     propertyDisplayValue: ""}]}];
                assert.deepEqual({ assetTypeId: 10, lon: 30.5, lat: 41.2, roadLinkId: 5, bearing: 95, imageIds: [], properties: expectedProperties}, assetCreationData[0]);
            });

            it('should remove direction arrow feature from direction arrow layer', function() {
                assert.equal(destroyedFeature.style.externalGraphic, 'src/resources/digiroad2/bundle/mapbusstop/images/suuntain.png');
            });

            xit('should show bus stop marker on marker layer', function() {
                assert.equal(addedMarker.id, 123);
            });

            it('should request bus stop infobox', function() {
                assert.equal(requests.length, 2);
                assert.deepEqual(showInfoBoxRequest, requests[0]);
                assert.equal('busStop', requestedInfoBoxType);
            });

            it('should request show of feature attributes', function() {
                assert.deepEqual(attributeShowRequest, requests[1]);
            });
        });

        describe('and when feature attribute collection has been cancelled', function() {
            before(function() {
                requests = [];
                destroyedFeature = {};
                collectionCancelledCallback();
            });

            it('should remove direction arrow feature from direction arrow layer', function() {
                assert.equal(destroyedFeature.style.externalGraphic, 'src/resources/digiroad2/bundle/mapbusstop/images/suuntain.png');
            });

            it('should remove bus stop infobox', function() {
                assert.deepEqual(hideInfoBoxRequest, requests[0]);
                assert.equal(hiddenInfoBoxId, 'busStop');
            });
        });
    });
});