/**
 * Handles Oskari.digiroad2.bundle.featureattributes.request.CollectFeatureAttributesRequest to collect bus stop attributes
 * from the end user.
 */
Oskari.clazz.define('Oskari.digiroad2.bundle.featureattributes.request.CollectFeatureAttributesRequestHandler',
    /**
     * @method create called automatically on construction
     * @static
     * @param {Oskari.digiroad2.bundle.featureattributes.FeatureAttributes}
     */

        function (featureAttributes) {
        this.featureAttributes = featureAttributes;
    }, {
        /**
         * @method handleRequest
         * @param {Oskari.mapframework.core.Core} core
         *      reference to the application core (reference sandbox core.getSandbox())
         * @param {Oskari.digiroad2.bundle.featureattributes.request.CollectFeatureAttributesRequest} request
         *      request to handle
         */
        handleRequest: function (core, request) {
            this.featureAttributes.collectAttributes(request.getAssetPosition(), request.getSuccessCallback(), request.getCancellationCallback());
        }
    }, {
        /**
         * @property {String[]} protocol array of superclasses as {String}
         * @static
         */
        protocol: ['Oskari.mapframework.core.RequestHandler']
    });