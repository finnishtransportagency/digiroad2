describe('FeatureAttributes', function () {
    var featureAttributesInstance = Oskari.clazz.define('Oskari.digiroad2.bundle.featureattributes.FeatureAttributesBundleInstance');

    describe('when backend returns undefined date', function () {
        var featureAttributes = Object.create(featureAttributesInstance._class.prototype);
        featureAttributes.init({});

        it('should construct date attribute with empty content', function () {
            var actualHtml = featureAttributes._makeContent([
                {
                    propertyId: 'propertyId',
                    propertyName: 'propertyName',
                    propertyType: 'date',
                    values: [
                        {imageId: null, propertyDisplayValue: null, propertyValue: 0}
                    ]
                }
            ]);
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

    describe('when user leaves date undefined', function () {
        var featureAttributes = null;
        var calls = [];

        before(function () {
            featureAttributes = Object.create(featureAttributesInstance._class.prototype);
            featureAttributes.init({
                backend: _.extend({}, window.Backend, {
                    putAssetPropertyValue: function (assetId, propertyId, data) {
                        calls.push(data);
                    },
                    getAsset: function (id, success) {
                        success({
                            propertyData: [createNullDateProperty('propertyId', 'propertyName')]
                        });
                    }
                })
            });
        });

        it('should send null date to backend', function () {
            calls = [];
            featureAttributes.showAttributes(130, { x: 24, y: 60, heading: 140 });
            var dateInput = $('input[data-propertyid="propertyId"]');
            dateInput.blur();
            assert.equal(1, calls.length);
            assert.deepEqual(calls[0], []);
        });

        function createNullDateProperty(propertyId, propertyName) {
            return {
                propertyId: propertyId,
                propertyName: propertyName,
                propertyType: 'date',
                values: [
                    {
                        imageId: null,
                        propertyDisplayValue: null,
                        propertyValue: 0
                    }
                ]};
        }
    });

    describe('when feature attribute collection is requested', function () {
        var featureAttributes = null;
        var requestedAssetTypes = [];

        before(function () {
            featureAttributes = Object.create(featureAttributesInstance._class.prototype);
            featureAttributes.init({
                backend: _.extend({}, window.Backend, {
                    getAssetTypeProperties: function (assetType, success) {
                        requestedAssetTypes.push(assetType);
                        var properties = [
                                { propertyId: '5', propertyName: 'Esteettömyystiedot', propertyType: 'text', required: false, values: [] },
                                { propertyId: '1', propertyName: 'Pysäkin katos', propertyType: 'single_choice', required: true, values: [] },
                                { propertyId: '6', propertyName: 'Ylläpitäjän tunnus', propertyType: 'text', required: false, values: [] },
                                { propertyId: '2', propertyName: 'Pysäkin tyyppi', propertyType: 'multiple_choice', required: true, values: [] },
                                { propertyId: '3', propertyName: 'Ylläpitäjä', propertyType: 'single_choice', required: true, values: [] },
                                { propertyId: '4', propertyName: 'Pysäkin saavutettavuus', propertyType: 'text', required: false, values: [] },
                                { propertyId: 'validityDirection', propertyName: 'Vaikutussuunta', propertyType: 'single_choice', required: false, values: [] },
                                { propertyId: 'validFrom', propertyName: 'Käytössä alkaen', propertyType: 'date', required: false, values: [] },
                                { propertyId: 'validTo', propertyName: 'Käytössä päättyen', propertyType: 'date', required: false, values: [] }
                        ];
                        success(properties);
                    }
                })
            });
        });

        it('should call backend for bus stop properties', function () {
            requestedAssetTypes = [];
            featureAttributes.collectAttributes(function() {});
            assert.equal(1, requestedAssetTypes.length);
            assert.equal(10, requestedAssetTypes[0]);
        });
    });
});
