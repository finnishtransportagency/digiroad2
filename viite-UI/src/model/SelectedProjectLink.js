(function(root) {
    root.SelectedProjectLink = function(projectLinkCollection) {

        var current = [];
        var open = function (linkid) {
            current = projectLinkCollection.getByLinkId(linkid);
            eventbus.trigger('projectLink:clicked', get());
        };
        var get = function() {
            return _.map(current, function(projectLink) {
                return projectLink.getData();
            });
        };

        return {
            open: open,
            get: get
        };
    };
})(this);
