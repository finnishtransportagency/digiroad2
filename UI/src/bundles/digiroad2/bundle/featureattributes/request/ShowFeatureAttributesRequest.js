/**
 * @class Oskari.digiroad2.bundle.featureattributes.request.ShowFeatureAttributesRequest
 * Requests a bus stop feature attributes to be shown
 *
 * Requests are build and sent through Oskari.mapframework.sandbox.Sandbox.
 * Oskari.mapframework.request.Request superclass documents how to send one.
 */
Oskari.clazz
    .define('Oskari.digiroad2.bundle.featureattributes.request.ShowFeatureAttributesRequest',
    /**
     * @method create called automatically on construction
     * @static
     *
     * @param {Object} assetAttributes
     *        Attributes of the asset to be shown
     * @param {Object} streetViewCoordinates
     *        Coordinate and heading information for Google street view integration
     *
     */

    function (assetAttributes, streetViewCoordinates) {
        this._creator = null;
        this._streetViewCoordinates = streetViewCoordinates;
        this._assetAttributes = assetAttributes;
    }, {
        /** @static @property __name request name */
        __name: "FeatureAttributes.ShowFeatureAttributesRequest",
        /**
         * @method getName
         * @return {String} request name
         */
        getName: function () {
            return this.__name;
        },

        /**
         * @method getStreetViewCoordinates
         * @return {Object} street view coordinates
         */
        getStreetViewCoordinates: function () {
            return this._streetViewCoordinates;
        },

        /**
         * @method getAssetAttributes
         * @return {Object} assetAttributes
         */
        getAssetAttributes: function () {
            return this._assetAttributes;
        }

    }, {
        /**
         * @property {String[]} protocol array of superclasses as {String}
         * @static
         */
        'protocol': ['Oskari.mapframework.request.Request']
    });