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
     * @param {String} title
     *        title
     * @param {Object} streetViewCoordinates
     *        Coordinate and heading information for Google street view integration
     *
     */

    function (title, streetViewCoordinates) {
        this._creator = null;
        this._streetViewCoordinates = streetViewCoordinates;
        this._title = title;
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
         * @method getTitle
         * @return {String} title
         */
        getTitle: function () {
            return this._title;
        }

    }, {
        /**
         * @property {String[]} protocol array of superclasses as {String}
         * @static
         */
        'protocol': ['Oskari.mapframework.request.Request']
    });