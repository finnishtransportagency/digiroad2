/**
 * @class Oskari.digiroad2.bundle.featureattributes.event.FeatureAttributeChangedEvent
 *
 * Used to notify components that ...
 */
Oskari.clazz.define('Oskari.digiroad2.bundle.featureattributes.event.FeatureAttributeChangedEvent',
    /**
     * @method create called automatically on construction
     * @static
     * @param {String} param some information you wish to communicate with the event
     */
        function(param) {
        this._param = param;
    }, {
        /** @static @property __name event name */
        __name : "featureattributes.FeatureAttributeChangedEvent",
        /**
         * @method getName
         * Returns event name
         * @return {String}
         */
        getName : function() {
            return this.__name;
        },
        /**
         * @method getParameter
         * Returns parameter that components reacting to event should know about
         * @return {String}
         */
        getParameter : function() {
            return this._param;
        }
    }, {
        'protocol' : ['Oskari.mapframework.event.Event']
});
