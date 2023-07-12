const assert = require('chai').assert;
const {ChangeSet} = require('../../dist/service/change-set');
const {KgvLink} = require("../../dist/client/kgv-client");
const {ReplaceInfo} = require("../../dist/client/vkm-client");

const testLinkGeom1 = "SRID=3067;LINESTRING ZM(331703.099 6668799.208 44.23 0,331699.994 6668800.316 44.069 3.297,331694.381 6668800.442 43.482 8.911,331686.381 6668799.798 42.825 16.937)";

describe('Change Set', function () {
    it('Change set without changes', function () {
        const emptyChangeSet = new ChangeSet([], []);
        assert.equal(emptyChangeSet.toJson(), '[]');
    });

    it('New link added', function () {
        const newLinkId = "testi:1";
        const newLink = new KgvLink(newLinkId, testLinkGeom1, 123, 3, 149, 16.93706266, 0, 12141);
        const change = new ReplaceInfo(undefined, newLinkId, undefined, undefined, 0, 16.937);
        const changeSet = new ChangeSet([newLink], [change]);
        const expected = [{
            "changeType": "add",
            "old": null,
            "new": [
                {
                    "linkId": newLinkId,
                    "linkLength": 16.937,
                    "geometry": testLinkGeom1,
                    "roadClass": 12141,
                    "adminClass": 3,
                    "municipality": 149,
                    "surfaceType": null,
                    "trafficDirection": 0
                }
            ],
            "replaceInfo": [
                {
                    "oldLinkId": null,
                    "newLinkId": newLinkId,
                    "oldFromMValue": null,
                    "oldToMValue": null,
                    "newFromMValue": 0,
                    "newToMValue": 16.937,
                    "digitizationChange": false
                }
            ]}];
        assert.equal(changeSet.changeEntries[0].changeType, "add");
        assert.equal(changeSet.toJson(), JSON.stringify(expected));
    });

    it('Link removed', function () {
        const linkId = "testi:1";
        const oldLink = new KgvLink(linkId, testLinkGeom1, 123, 3, 149, 16.93706266, 0, 12141);
        const change = new ReplaceInfo(linkId, undefined, 0, 16.937, undefined, undefined);
        const changeSet = new ChangeSet([oldLink], [change]);
        const expected = [{
            "changeType": "remove",
            "old": {
                "linkId": linkId,
                "linkLength": 16.937,
                "geometry": testLinkGeom1,
                "roadClass": 12141,
                "adminClass": 3,
                "municipality": 149,
                "surfaceType": null,
                "trafficDirection": 0
            },
            "new": [],
            "replaceInfo": [
                {
                    "oldLinkId": linkId,
                    "newLinkId": null,
                    "oldFromMValue": 0,
                    "oldToMValue": 16.937,
                    "newFromMValue": null,
                    "newToMValue": null,
                    "digitizationChange": false
                }
            ]}];
        assert.equal(changeSet.changeEntries[0].changeType, "remove");
        assert.equal(changeSet.toJson(), JSON.stringify(expected));
    });

    it('Link replaced', function () {
        const oldLinkId = "link:1";
        const newLinkId = "link:2";
        const oldLink = new KgvLink(oldLinkId, testLinkGeom1, 123, 3, 149, 16.93706266, 0, undefined, 1);
        const newLink = new KgvLink(newLinkId, testLinkGeom1, 124, 3, 149, 16.93786266, 0, 12141, 2);
        const change = new ReplaceInfo(oldLinkId, newLinkId, 0, 16.937, 0, 16.937);
        const changeSet = new ChangeSet([oldLink, newLink], [change]);
        const expected = [{
            "changeType": "replace",
            "old": {
                "linkId": oldLinkId,
                "linkLength": 16.937,
                "geometry": testLinkGeom1,
                "roadClass": null,
                "adminClass": 3,
                "municipality": 149,
                "surfaceType": 1,
                "trafficDirection": 0
            },
            "new": [
                {
                    "linkId": newLinkId,
                    "linkLength": 16.938,
                    "geometry": testLinkGeom1,
                    "roadClass": 12141,
                    "adminClass": 3,
                    "municipality": 149,
                    "surfaceType": 2,
                    "trafficDirection": 0
                }
            ],
            "replaceInfo": [
                {
                    "oldLinkId": oldLinkId,
                    "newLinkId": newLinkId,
                    "oldFromMValue": 0,
                    "oldToMValue": 16.937,
                    "newFromMValue": 0,
                    "newToMValue": 16.937,
                    "digitizationChange": false
                }
            ]}];
        assert.equal(changeSet.changeEntries[0].changeType, "replace");
        assert.equal(changeSet.toJson(), JSON.stringify(expected));
    });

    it('Link split to multiple new links', function () {
        const oldGeom  = 'SRID=3067;LINESTRING ZM(463081.329 6750374.612 78.202 0,463082.714 6750385.729 78.269 11.203,463084.009 6750395.805 78.593 21.362,463086.129 6750410.791 79.371 36.497,463087.523 6750420.157 79.826 45.966,463086.688 6750429.784 80.3 55.629,463084.137 6750441.187 80.807 67.314,463080.461 6750451.094 81.275 77.881,463075.128 6750461.473 81.803 89.55,463070.188 6750469.693 82.19 99.14,463065.041 6750476.849 82.491 107.955,463056.939 6750486.33 82.464 120.426)';
        const newGeom1 = 'SRID=3067;LINESTRING ZM(463081.329 6750374.612 78.202 0,463081.819 6750378.557 78.147 3.975)';
        const newGeom2 = 'SRID=3067;LINESTRING ZM(463081.819 6750378.557 78.147 0,463082.714 6750385.729 78.269 7.228,463084.009 6750395.805 78.593 17.387,463086.129 6750410.791 79.371 32.522,463087.523 6750420.157 79.826 41.991,463086.688 6750429.784 80.3 51.654,463084.137 6750441.187 80.807 63.339,463080.461 6750451.094 81.275 73.906,463075.128 6750461.473 81.803 85.575,463070.188 6750469.693 82.19 95.165,463065.041 6750476.849 82.491 103.98,463060.195 6750482.577 82.567 111.483)';
        const newGeom3 = 'SRID=3067;LINESTRING ZM(463060.195 6750482.577 82.567 0,463056.939 6750486.33 82.464 4.969)';

        const oldLink  = new KgvLink("old:1", oldGeom, 123, 2, 142, 120.42636809, 0, 12131);
        const newLink1 = new KgvLink("new1:1", newGeom1, 124, 2, 142, 3.97531445, 0, 12131);
        const newLink2 = new KgvLink("new2:2", newGeom2, 125, 2, 142, 111.48272932, 0, 12131);
        const newLink3 = new KgvLink("new3:3", newGeom3, 126, 2, 142, 4.96855563, 0, 12131);

        const changes = [
            new ReplaceInfo(oldLink.id, newLink1.id, 0, 3.975, 0, 3.975),
            new ReplaceInfo(oldLink.id, newLink2.id, 3.975, 115.458, 0, 111.483),
            new ReplaceInfo(oldLink.id, newLink3.id, 120.426, 115.458, 0, 4.968)
        ];

        const changeSet = new ChangeSet([oldLink, newLink1, newLink2, newLink3], changes);
        const expected = [{
            "changeType": "split",
            "old": {
                "linkId": oldLink.id,
                "linkLength": oldLink.length,
                "geometry": oldLink.geometry,
                "roadClass": oldLink.roadClass,
                "adminClass": oldLink.adminClass,
                "municipality": oldLink.municipality,
                "surfaceType": null,
                "trafficDirection": oldLink.directionType
            },
            "new": [
                {
                    "linkId": newLink1.id,
                    "linkLength": newLink1.length,
                    "geometry": newLink1.geometry,
                    "roadClass": newLink1.roadClass,
                    "adminClass": newLink1.adminClass,
                    "municipality": newLink1.municipality,
                    "surfaceType": null,
                    "trafficDirection": newLink1.directionType
                },
                {
                    "linkId": newLink2.id,
                    "linkLength": newLink2.length,
                    "geometry": newLink2.geometry,
                    "roadClass": newLink2.roadClass,
                    "adminClass": newLink2.adminClass,
                    "municipality": newLink2.municipality,
                    "surfaceType": null,
                    "trafficDirection": newLink2.directionType
                },
                {
                    "linkId": newLink3.id,
                    "linkLength": newLink3.length,
                    "geometry": newLink3.geometry,
                    "roadClass": newLink3.roadClass,
                    "adminClass": newLink3.adminClass,
                    "municipality": newLink3.municipality,
                    "surfaceType": null,
                    "trafficDirection": newLink3.directionType
                }
            ],
            "replaceInfo": [
                {
                    "oldLinkId": oldLink.id,
                    "newLinkId": newLink1.id,
                    "oldFromMValue": 0,
                    "oldToMValue": 3.975,
                    "newFromMValue": 0,
                    "newToMValue": 3.975,
                    "digitizationChange": false
                },
                {
                    "oldLinkId": oldLink.id,
                    "newLinkId": newLink2.id,
                    "oldFromMValue": 3.975,
                    "oldToMValue": 115.458,
                    "newFromMValue": 0,
                    "newToMValue": 111.483,
                    "digitizationChange": false
                },
                {
                    "oldLinkId": oldLink.id,
                    "newLinkId": newLink3.id,
                    "oldFromMValue": 120.426,
                    "oldToMValue": 115.458,
                    "newFromMValue": 0,
                    "newToMValue": 4.968,
                    "digitizationChange": true
                }
            ]
        }];
        assert.equal(changeSet.changeEntries[0].changeType, "split");
        assert.equal(changeSet.toJson(), JSON.stringify(expected));
    });

    it('Link split, one part is removed and other moved', function () {
        const oldGeom  = 'SRID=3067;LINESTRING ZM(463081.329 6750374.612 78.202 0,463082.714 6750385.729 78.269 11.203,463084.009 6750395.805 78.593 21.362,463086.129 6750410.791 79.371 36.497,463087.523 6750420.157 79.826 45.966,463086.688 6750429.784 80.3 55.629,463084.137 6750441.187 80.807 67.314,463080.461 6750451.094 81.275 77.881,463075.128 6750461.473 81.803 89.55,463070.188 6750469.693 82.19 99.14,463065.041 6750476.849 82.491 107.955,463056.939 6750486.33 82.464 120.426)';
        const newGeom1 = 'SRID=3067;LINESTRING ZM(463081.819 6750378.557 78.147 0,463082.714 6750385.729 78.269 7.228,463084.009 6750395.805 78.593 17.387,463086.129 6750410.791 79.371 32.522,463087.523 6750420.157 79.826 41.991,463086.688 6750429.784 80.3 51.654,463084.137 6750441.187 80.807 63.339,463080.461 6750451.094 81.275 73.906,463075.128 6750461.473 81.803 85.575,463070.188 6750469.693 82.19 95.165,463065.041 6750476.849 82.491 103.98,463060.195 6750482.577 82.567 111.483)';

        const oldLink  = new KgvLink("old:1", oldGeom, 123, 2, 142, 120.42636809, 0, 12131);
        const newLink1 = new KgvLink("new2:1", newGeom1, 125, 2, 142, 111.48272932, 0, 12131);

        const changes = [
            new ReplaceInfo(oldLink.id, null, 0, 3.975, null, null),
            new ReplaceInfo(oldLink.id, newLink1.id, 3.975, 115.458, 0, 111.483),
        ];

        const changeSet = new ChangeSet([oldLink, newLink1], changes);
        const expected = [{
            "changeType": "split",
            "old": {
                "linkId": oldLink.id,
                "linkLength": oldLink.length,
                "geometry": oldLink.geometry,
                "roadClass": oldLink.roadClass,
                "adminClass": oldLink.adminClass,
                "municipality": oldLink.municipality,
                "surfaceType": null,
                "trafficDirection": oldLink.directionType
            },
            "new": [
                {
                    "linkId": newLink1.id,
                    "linkLength": newLink1.length,
                    "geometry": newLink1.geometry,
                    "roadClass": newLink1.roadClass,
                    "adminClass": newLink1.adminClass,
                    "municipality": newLink1.municipality,
                    "surfaceType": null,
                    "trafficDirection": newLink1.directionType
                },
            ],
            "replaceInfo": [
                {
                    "oldLinkId": oldLink.id,
                    "newLinkId": null,
                    "oldFromMValue": 0,
                    "oldToMValue": 3.975,
                    "newFromMValue": 0,
                    "newToMValue": 0,
                    "digitizationChange": false
                },
                {
                    "oldLinkId": oldLink.id,
                    "newLinkId": newLink1.id,
                    "oldFromMValue": 3.975,
                    "oldToMValue": 115.458,
                    "newFromMValue": 0,
                    "newToMValue": 111.483,
                    "digitizationChange": false
                },
            ]
        }];
        assert.equal(changeSet.changeEntries[0].changeType, "split");
        assert.equal(changeSet.toJson(), JSON.stringify(expected));
    });

    it('Links merged', function () {
        const oldGeom1 = 'SRID=3067;LINESTRING ZM(426632.935 7111261.572 96.606 0,426634.266 7111272.439 96.676 10.948,426637.384 7111283.768 96.976 22.698,426641.674 7111296.6 97.275 36.229,426648.54 7111310.958 97.832 52.144,426654.383 7111321.984 98.023 64.622,426654.483 7111322.164 98.023 64.828)';
        const oldGeom2 = 'SRID=3067;LINESTRING ZM(426654.483 7111322.164 98.023 0,426658.886 7111330.74 98.044 9.64,426661.739 7111340.464 97.981 19.774,426664.116 7111346.486 97.987 26.248,426668.526 7111350.387 97.956 32.136)';
        const newGeom  = 'SRID=3067;LINESTRING ZM(426632.935 7111261.572 96.606 0,426634.266 7111272.439 96.676 10.948,426637.384 7111283.768 96.976 22.698,426641.674 7111296.6 97.275 36.229,426648.54 7111310.958 97.832 52.144,426654.383 7111321.984 98.023 64.622,426654.483 7111322.164 98.023 64.828,426658.886 7111330.74 98.044 74.468,426661.739 7111340.464 97.981 84.602,426664.116 7111346.486 97.987 91.076,426668.526 7111350.387 97.956 96.964)';

        const oldLink1 = new KgvLink("old1:1", oldGeom1, 123, 3, 71, 64.82821897, 0, 12141);
        const oldLink2 = new KgvLink("old2:1", oldGeom2, 124, 3, 71, 32.13605585, 0, 12141);
        const newLink  = new KgvLink("new:1", newGeom, 125, 3, 71, 96.96427481, 0, 12141);

        const changes = [
            new ReplaceInfo(oldLink1.id, newLink.id, 0, 64.828, 0, 64.82796475216877),
            new ReplaceInfo(oldLink2.id, newLink.id, 0, 32.136, 64.828, 96.964),
        ];

        const changeSet = new ChangeSet([oldLink1, oldLink2, newLink], changes);
        const expected = [
            {
                "changeType": "replace",
                "old": {
                    "linkId": oldLink1.id,
                    "linkLength": oldLink1.length,
                    "geometry": oldLink1.geometry,
                    "roadClass": oldLink1.roadClass,
                    "adminClass": oldLink1.adminClass,
                    "municipality": oldLink1.municipality,
                    "surfaceType": null,
                    "trafficDirection": oldLink1.directionType
                },
                "new": [
                    {
                        "linkId": newLink.id,
                        "linkLength": newLink.length,
                        "geometry": newLink.geometry,
                        "roadClass": newLink.roadClass,
                        "adminClass": newLink.adminClass,
                        "municipality": newLink.municipality,
                        "surfaceType": null,
                        "trafficDirection": newLink.directionType
                    }
                ],
                "replaceInfo": [
                    {
                        "oldLinkId": oldLink1.id,
                        "newLinkId": newLink.id,
                        "oldFromMValue": 0,
                        "oldToMValue": 64.828,
                        "newFromMValue": 0,
                        "newToMValue": 64.82796475216877,
                        "digitizationChange": false
                    }
                ]
            },
            {
                "changeType": "replace",
                "old": {
                    "linkId": oldLink2.id,
                    "linkLength": oldLink2.length,
                    "geometry": oldLink2.geometry,
                    "roadClass": oldLink2.roadClass,
                    "adminClass": oldLink2.adminClass,
                    "municipality": oldLink2.municipality,
                    "surfaceType": null,
                    "trafficDirection": oldLink2.directionType
                },
                "new": [
                    {
                        "linkId": newLink.id,
                        "linkLength": newLink.length,
                        "geometry": newLink.geometry,
                        "roadClass": newLink.roadClass,
                        "adminClass": newLink.adminClass,
                        "municipality": newLink.municipality,
                        "surfaceType": null,
                        "trafficDirection": newLink.directionType
                    }
                ],
                "replaceInfo": [
                    {
                        "oldLinkId": oldLink2.id,
                        "newLinkId": newLink.id,
                        "oldFromMValue": 0,
                        "oldToMValue": 32.136,
                        "newFromMValue": 64.828,
                        "newToMValue": 96.964,
                        "digitizationChange": false
                    }
                ]
            }
        ];
        assert.equal(changeSet.changeEntries[0].changeType, "replace");
        assert.equal(changeSet.changeEntries[1].changeType, "replace");
        assert.equal(changeSet.toJson(), JSON.stringify(expected));
    });
});