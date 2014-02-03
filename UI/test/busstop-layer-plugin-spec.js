
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
});