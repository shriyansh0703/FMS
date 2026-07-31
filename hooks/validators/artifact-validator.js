'use strict';

const { ARTIFACT_OWNER, VALID_APPROVAL_STATUSES, ANY_STAGE } = require('../utils/config.js');
const { canonicalKey, stageLabel } = require('../utils/stage-keys.js');

/**
 * Verify the artifact belongs to the current stage.
 *
 * Both sides are canonicalised first. Previously this compared an owner key
 * ('hld_backend') against whatever workflow-state.json happened to hold for
 * currentStage (the number 4), so the comparison was meaningless.
 *
 * @param {string} artifactName
 * @param {string|number} currentStage Any stage form: 4, '3a', 'hld_review'
 * @param {string} [scope] Disambiguates bare stage numbers 3 and 5
 * @returns {{valid: boolean, errors: string[]}}
 */
const validateArtifactOwnership = (artifactName, currentStage, scope) => {
    const errors = [];
    const owner = ARTIFACT_OWNER[artifactName];

    if (!owner) {
        errors.push(`Unknown artifact: ${artifactName}`);
        return { valid: false, errors };
    }

    // Incrementally-maintained artifacts (traceability.md) have no single owner.
    if (owner === ANY_STAGE) {
        return { valid: true, errors };
    }

    const currentKey = canonicalKey(currentStage, scope);

    // If we cannot resolve the current stage, do not manufacture a violation —
    // that would block all writes on a malformed state file.
    if (currentKey === null) {
        return { valid: true, errors };
    }

    if (owner !== currentKey) {
        errors.push(
            `Artifact ${artifactName} is owned by ${stageLabel(owner)}, but the ` +
            `workflow is at ${stageLabel(currentKey)}. Advance the stage before ` +
            `writing this artifact, or correct .ai/state/workflow-state.json.`
        );
    }

    return { valid: errors.length === 0, errors };
};

/**
 * Check version, createdAt, updatedAt, stage, status, checksum, lastModifiedBySkill, approvalStatus are present.
 * @param {Object} metadata 
 * @returns {{valid: boolean, errors: string[]}}
 */
const validateArtifactMetadata = (metadata) => {
    const errors = [];
    if (!metadata || typeof metadata !== 'object') {
        return { valid: false, errors: ['Metadata must be a valid object'] };
    }
    
    const requiredFields = ['version', 'createdAt', 'updatedAt', 'stage', 'status', 'checksum', 'lastModifiedBySkill', 'approvalStatus'];
    requiredFields.forEach(field => {
        if (!(field in metadata)) {
            errors.push(`Missing required metadata field: ${field}`);
        }
    });
    
    if (metadata.approvalStatus && !VALID_APPROVAL_STATUSES.includes(metadata.approvalStatus)) {
        errors.push(`Invalid approval status: ${metadata.approvalStatus}`);
    }
    
    return { valid: errors.length === 0, errors };
};

module.exports = { validateArtifactOwnership, validateArtifactMetadata };
