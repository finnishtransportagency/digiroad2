
var assert = chai.assert;

describe('BusStopLayerPlugin', function(){
    var busStopPlugin = Oskari.clazz.define('Oskari.digiroad2.bundle.mapbusstop.plugin.BusStopLayerPlugin');

    var dataOneRowJson = {"nimi1" :" arvo 1"};
    var testOneRowHtml =  ['<li>nimi1<input type="text" name="nimi1" value=" arvo 1"></li>'];

    var dataTwoRowJson = {"nimi1" :" arvo 1","nimi2" :" arvo 2"};
    var testTwoRowHtml =  ['<li>nimi1<input type="text" name="nimi1" value=" arvo 1"></li>', '<li>nimi2<input type="text" name="nimi2" value=" arvo 2"></li>'];



    before(function(){
        busStopPlugin._class.prototype._initTemplates();
    });

    describe('#makeContent()', function(){
        it('should return one row', function(){
            assert.equal(testOneRowHtml[0],busStopPlugin._class.prototype._makeContent(dataOneRowJson).html[0]);
        });

        it('should return one row and empty actions object', function(){
            var contentItem = {
                html : testOneRowHtml,
                actions : {}
            };
            assert.deepEqual(contentItem,busStopPlugin._class.prototype._makeContent(dataOneRowJson));
        });

        it('should return two rows', function(){
            assert.equal(testTwoRowHtml[0],busStopPlugin._class.prototype._makeContent(dataTwoRowJson).html[0]);
            assert.equal(testTwoRowHtml[1],busStopPlugin._class.prototype._makeContent(dataTwoRowJson).html[1]);
        });

        it('should return two rows and empty actions object', function(){

            var contentItem = {
                html : testTwoRowHtml,
                actions : {}
            };

            assert.deepEqual(contentItem,busStopPlugin._class.prototype._makeContent(dataTwoRowJson));
        });
    });
});