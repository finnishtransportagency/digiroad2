(function(root) {
    root.LinearSuggestionLabel = function(){

        AssetLabel.call(this);
        var me = this;

        this.suggestionStyle = function(isSuggested, position) {
            return !_.isUndefined(isSuggested) && isSuggested ?
                me.getSuggestionStyle(position) : [];
        };

        this.getSuggestionStyle = function (position) {
            return new ol.style.Style({
                image: new ol.style.Icon(({
                    src: 'images/icons/questionMarker.png',
                    anchor : [19+position.x, 61+position.y],
                    anchorXUnits: "pixels",
                    anchorYUnits: "pixels"
                }))
            });
        };
    };
})(this);
