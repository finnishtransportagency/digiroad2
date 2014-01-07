
var assert = chai.assert;

describe('BusStopLayerPlugin', function(){
    var busStopPlugin = Oskari.clazz.define('Oskari.digiroad2.bundle.mapbusstop.plugin.BusStopLayerPlugin');

    var dataOneBusStopType = ["2"];
    var testOneBusStopTypeHtml =  '<img src="/api/images/2">';

    var dataTwoBusStopType = ["2","3"];
    var testTwoBusStopTypeHtml =  '<img src="/api/images/2"><img src="/api/images/3">';

    var dataEmptyBusStopType = [];
    var testEmptyBusStopTypeHtml =  '';

    before(function(){
        busStopPlugin._class.prototype._initTemplates();
    });

    describe('#makePopupContent()', function(){
        it('should return one bus stop html by image tag', function(){
            assert.equal(testOneBusStopTypeHtml,busStopPlugin._class.prototype._makePopupContent(dataOneBusStopType));
        });

        it('should return two various bus stop html by image tags', function(){
            assert.equal(testTwoBusStopTypeHtml,busStopPlugin._class.prototype._makePopupContent(dataTwoBusStopType));
        });

        it('should return empty html', function(){
            assert.equal(testEmptyBusStopTypeHtml,busStopPlugin._class.prototype._makePopupContent(dataEmptyBusStopType));
        });

    });
});