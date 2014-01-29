describe('FeatureAttributes', function() {
    var featureAttributesInstance = Oskari.clazz.define('Oskari.digiroad2.bundle.featureattributes.FeatureAttributesBundleInstance');

    describe('when backend returns undefined date', function() {
        var featureAttributes = Object.create(featureAttributesInstance._class.prototype);
        featureAttributes.init();

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
});
