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
     * @param {Object[]} contentData
     *        JSON presentation for the data
     *
     */

        function (title, content) {
        this._creator = null;
        this._content = content;
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
         * @method getContent
         * @return {Object[]} content
         */
        getContent: function () {
            return this._content;
        },

        /**
         * @method getTitle
         * @return {String} content
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