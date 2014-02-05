
var assert = chai.assert;

describe('BusStopLayerPlugin', function(){
    var busStopPlugin = Oskari.clazz.create('Oskari.digiroad2.bundle.mapbusstop.plugin.BusStopLayerPlugin',{});
    console.log(busStopPlugin);
    //var busStopPlugin = Object.create(busStopPluginInstance._class.prototype);

    var dataOneBusStopType = ["2"];
    var testOneBusStopTypeHtml =  '<img src="/api/images/2">';

    var dataTwoBusStopType = ["2","3"];
    var testTwoBusStopTypeHtml =  '<img src="/api/images/2"><img src="/api/images/3">';

    var dataEmptyBusStopType = [];
    var testEmptyBusStopTypeHtml =  '';

    var oneIconImageHtml =
        '<div class="callout">' +
            '<img src="/api/images/2.png">' +
            '<div class="arrow-container">' +
                '<div class="arrow"></div>' +
            '</div>' +
        '<div class="dropHandle">' +
        '</div></div>';
    var twoIconImageHtml =
        '<div class="callout">' +
            '<img src="/api/images/2.png">' +
            '<img src="/api/images/3.png">' +
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
        busStopPlugin._initTemplates();
    });

    describe('#makePopupContent()', function(){
        it('should return one bus stop html by image tag', function(){
            assert.equal(testOneBusStopTypeHtml,busStopPlugin._makePopupContent(dataOneBusStopType));
        });

        it('should return two various bus stop html by image tags', function(){
            assert.equal(testTwoBusStopTypeHtml,busStopPlugin._makePopupContent(dataTwoBusStopType));
        });

        it('should return empty html', function(){
            assert.equal(testEmptyBusStopTypeHtml,busStopPlugin._makePopupContent(dataEmptyBusStopType));
        });
    });

    describe('#getIconImages()', function(){
        it('should return one bus stop html by image tag', function(){
            assert.equal(oneIconImageHtml,busStopPlugin._getIconImages(dataOneBusStopType).outerHTML);
        });
        it('should return two bus stop html by images tag', function(){
            assert.equal(twoIconImageHtml,busStopPlugin._getIconImages(dataTwoBusStopType).outerHTML);
        });
        it('should return html without images', function(){
            assert.equal(noIconImageHtml,busStopPlugin._getIconImages(dataEmptyBusStopType).outerHTML);
        });
    });
});