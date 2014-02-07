/**
 * Request to commence collection of bus stop feature attributes from end user
 */
Oskari.clazz.define('Oskari.digiroad2.bundle.featureattributes.request.CollectFeatureAttributesRequest',
    /**
     * @method create called automatically on construction
     * @static
     *
     * @param {Function} successCallback
     *        Function that will be called after user has entered bus stop feature attributes from end user.
     *        Function will received collected attribute data as function parameter
     */

        function (successCallback) {
        this._creator = null;
        this._successCallback = successCallback;

    }, {
        /** @static @property __name request name */
        __name: "FeatureAttributes.CollectFeatureAttributesRequest",
        /**
         * @method getName
         * @return {String} request name
         */
        getName: function () {
            return this.__name;
        },
        /**
         * @method getSuccessCallback
         * @return {Function} successCallback
         */
        getSuccessCallback: function () {
            return this._successCallback;
        }

    }, {
        /**
         * @property {String[]} protocol array of superclasses as {String}
         * @static
         */
        'protocol': ['Oskari.mapframework.request.Request']
    });
