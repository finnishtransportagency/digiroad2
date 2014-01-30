describe('FeatureAttributes', function() {
    var featureAttributesInstance = Oskari.clazz.define('Oskari.digiroad2.bundle.featureattributes.FeatureAttributesBundleInstance');

    describe('when backend returns undefined date', function() {
        var featureAttributes = Object.create(featureAttributesInstance._class.prototype);
        featureAttributes.init({});

        it('should construct date attribute with empty content', function() {
            var actualHtml = featureAttributes._makeContent([{
                propertyId: 'propertyId',
                propertyName: 'propertyName',
                propertyType: 'date',
                values: [{imageId: null, propertyDisplayValue: null, propertyValue: 0}]
            }]);
            assert.equal(actualHtml,
                '<div class="formAttributeContentRow">' +
                    '<div class="formLabels">propertyName</div>' +
                    '<div class="formAttributeContent">' +
                        '<input class="featureAttributeDate" type="text" data-propertyId="propertyId" name="propertyName" value=""/>' +
                        '<span class="attributeFormat">pp.kk.vvvv</span>' +
                    '</div>' +
                '</div>');
        });
    });

    describe('when user leaves date undefined', function() {
        var featureAttributes = null;
        var calls = [];

        before(function() {
            featureAttributes = Object.create(featureAttributesInstance._class.prototype);
            featureAttributes.init({
                backend: _.extend({}, window.Backend, {
                    putAssetPropertyValue: function(assetId, propertyId, data) { calls = calls.concat(data); },
                    getAsset: function(id, success) {
                        success({
                            propertyData: [createNullDateProperty('propertyId', 'propertyName')]
                        });
                    }
                })
            });
        });

        it('should send null date to backend', function() {
            calls = [];
            featureAttributes.showAttributes(130, { x: 24, y: 60, heading: 140 });
            var dateInput = $('input[data-propertyid="propertyId"]');
            dateInput.blur();
            assert.equal(1, calls.length);
            assert.deepEqual(calls[0], { propertyValue:0, propertyDisplayValue:'Invalid date' });
        });

        function createNullDateProperty(propertyId, propertyName) {
            return {
                propertyId: propertyId,
                propertyName: propertyName,
                propertyType: 'date',
                values: [{
                imageId: null,
                propertyDisplayValue: null,
                propertyValue: 0
            }]};
        }
    });
});
