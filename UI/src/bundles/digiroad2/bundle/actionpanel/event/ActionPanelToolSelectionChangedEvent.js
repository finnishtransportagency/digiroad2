/**
* @class Oskari.digiroad2.bundle.actionpanel.event.ActionPanelToolSelectionChangedEvent
*
* Used to notify components that ...
    */
Oskari.clazz.define('Oskari.digiroad2.bundle.actionpanel.event.ActionPanelToolSelectionChangedEvent',
    /**
     * @method create called automatically on construction
     * @static
     * @param {String} action information you wish to communicate with the event
     */
        function(action) {
        this._action = action;
    }, {
        /** @static @property __name event name */
        __name : "actionpanel.ActionPanelToolSelectionChangedEvent",
        /**
         * @method getName
         * Returns event name
         * @return {String}
         */
        getName : function() {
            return this.__name;
        },
        /**
         * @method getAction
         * Returns action that components reacting to event should know about
         * @return {String}
         */
        getAction : function() {
            return this._action;
        }
    }, {
        'protocol' : ['Oskari.mapframework.event.Event']
    });