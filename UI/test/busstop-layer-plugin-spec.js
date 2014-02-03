
var assert = chai.assert;

describe('BusStopLayerPlugin', function(){
    var busStopPlugin = Oskari.clazz.define('Oskari.digiroad2.bundle.mapbusstop.plugin.BusStopLayerPlugin');

    describe('#makePopupContent()', function() {
        var pluginInstance = null;

        var dataOneBusStopType = ["2"];
        var testOneBusStopTypeHtml =  '<img src="/api/images/2">';

        var dataTwoBusStopType = ["2","3"];
        var testTwoBusStopTypeHtml =  '<img src="/api/images/2"><img src="/api/images/3">';

        var dataEmptyBusStopType = [];
        var testEmptyBusStopTypeHtml =  '';

        before(function(){
            pluginInstance = Object.create(busStopPlugin._class.prototype);
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

    describe('when adding a new bus stop', function() {
        var pluginInstance = null;
        var request = null;
        var requestCallback = null;
        var attributeCollectionRequest = {};
        var attributeCollectionRequestBuilder = function(callback) {
            requestCallback = callback;
            return attributeCollectionRequest;
        };

        before(function() {
            pluginInstance = Object.create(busStopPlugin._class.prototype);
            pluginInstance.setMapModule({
                getName: function() { return 'MapModule'; },
                getMap: function() { return {}; }
            });
            pluginInstance.startPlugin({
                register: function() {},
                registerForEventByName: function() {},
                getRequestBuilder: function(request) {
                    return request === 'FeatureAttributes.CollectFeatureAttributesRequest' ? attributeCollectionRequestBuilder : null;
                },
                request: function(name, r) { request = r; }
            });
            pluginInstance._toolSelectionChange({
                getAction: function() { return 'AddWithCollection'; }
            });
            pluginInstance._addBusStopEvent({});
        });

        it('should request collection of feature attributes', function() {
            assert.equal(request, attributeCollectionRequest);
        });
    });
});