(function(root) {
  root.LinearAssetAuthorizationPolicyWithSuggestion = function() {
    LinearAssetAuthorizationPolicy.call(this);

    var me = this;

        this.handleSuggestedAsset = function(selectedAsset, value, layerMode) {
          if (layerMode === 'readOnly')
            return !!parseInt(value);
          else
            return !selectedAsset.isSplitOrSeparated() && (_.isNull((selectedAsset.getId()) && me.isOperator()) || (!!parseInt(value) && (me.isOperator() || me.isMunicipalityMaintainer())));
        };
  };
})(this);