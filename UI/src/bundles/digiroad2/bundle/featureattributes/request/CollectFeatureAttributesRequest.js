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
     * @param {Function} cancellationCallback
     *        Function that will be called when user has cancelled feature attribute collection.
     */

        function (successCallback, cancellationCallback) {
        this._creator = null;
        this._successCallback = successCallback;
        this._cancellationCallback = cancellationCallback;

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
        },
        /**
         * @method getCancellationCallback
         * @return {Function} cancellationCallback
         */
        getCancellationCallback: function () {
            return this._cancellationCallback;
        }

    }, {
        /**
         * @property {String[]} protocol array of superclasses as {String}
         * @static
         */
        'protocol': ['Oskari.mapframework.request.Request']
    });
