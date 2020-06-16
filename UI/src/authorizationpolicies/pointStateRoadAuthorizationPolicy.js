(function (root) {
    root.PointStateRoadAuthorizationPolicy = function () {
        AuthorizationPolicy.call(this);
        var me = this;

        this.formEditModeAccess = function (selectedAsset) {
            var isMaintainerAndHaveRights = (me.isMunicipalityMaintainer()) || me.isElyMaintainer() && me.hasRightsInMunicipality(selectedAsset.getMunicipalityCode());

            return me.isStateExclusions(selectedAsset) || (( isMaintainerAndHaveRights || me.isOperator()) && !me.isState(selectedAsset.get()));
        };

        this.filterRoadLinks = function (roadLink) {
            var isMaintainerAndHaveRights = (me.isMunicipalityMaintainer()) || me.isElyMaintainer() && me.hasRightsInMunicipality(roadLink.municipalityCode);

            return me.isStateExclusions(roadLink) ||(( isMaintainerAndHaveRights || me.isOperator()) && !me.isState(roadLink) );
        };
    };
})(this);