/**
 * @class Oskari.digiroad2.bundle.featureattributes.request.ShowFeatureAttributesRequestHandler
 * Handles Oskari.digiroad2.bundle.featureattributes.request.ShowFeatureAttributesRequest to show an attributes
 * in feature data grid.
 */
Oskari.clazz.define('Oskari.digiroad2.bundle.featureattributes.request.ShowFeatureAttributesRequestHandler',
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
         * Shows an feature attributes grid with requested properties
         * @param {Oskari.mapframework.core.Core} core
         *      reference to the application core (reference sandbox core.getSandbox())
         * @param {Oskari.digiroad2.bundle.featureattributes.request.ShowFeatureAttributesRequest} request
         *      request to handle
         */
        handleRequest: function (core, request) {

           this.featureAttributes.showAttributes(
                request.getTitle(),
                request.getContent()
            );
        }
    }, {
        /**
         * @property {String[]} protocol array of superclasses as {String}
         * @static
         */
        protocol: ['Oskari.mapframework.core.RequestHandler']
    });