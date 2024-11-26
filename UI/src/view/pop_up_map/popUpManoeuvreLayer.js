(function(root){
    root.PopUpManoeuvreLayer = function(map, roadLayer, manoeuvresCollection, suggestionLabel) {
        var me = this;
        BaseManoeuvreLayer.call(this, map, roadLayer, manoeuvresCollection, suggestionLabel);

        /*
         * ------------------------------------------
         *  Public methods
         * ------------------------------------------
         */

        var hide = function() {
            me.hideLayer();
        };

        var refreshWorkListView = function (assetId) {
            manoeuvresCollection.fetchManoeuvresOnExpiredLinks(assetId, me.draw);
        };

        return {
            hide: hide,
            refreshWorkListView: refreshWorkListView
        };
    };
})(this);