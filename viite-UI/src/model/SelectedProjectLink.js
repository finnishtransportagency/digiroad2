(function(root) {
    root.SelectedProjectLink = function(projectLinkCollection) {

        var current = [];
        var open = function (event) {
            current = projectLinkCollection.getByLinkId();
            eventbus.trigger('projectLink:clicked');
        };

        return {
            open: open
        };
    };
})(this);
