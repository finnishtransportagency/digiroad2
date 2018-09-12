(function (root) {
    root.PointStateRoadAuthorizationPolicy = function () {
        AuthorizationPolicy.call(this);
        var me = this;
        this.formEditModeAccess = function (selectedAsset) {
            return ((me.isMunicipalityMaintainer() || me.isElyMaintainer() && me.hasRightsInMunicipality(selectedAsset.getMunicipalityCode())) || me.isOperator()) && !me.isState(selectedAsset.get());
        };
        this.filterRoadLinks = function (roadLink) {
            return ((me.isMunicipalityMaintainer() || me.isElyMaintainer()) && me.hasRightsInMunicipality(roadLink.municipalityCode) || me.isOperator()) && !me.isState(roadLink);
        };
    };
})(this);