#include <algorithm>
#include <cmath>
#include <cstdint>
#include <functional>
#include <limits>
#include <queue>
#include <string>
#include <unordered_map>
#include <vector>

#include <Level.hpp>
#include <gdr/gdr.hpp>

#include "pathfinder.hpp"

class Replay2 : public gdr::Replay<Replay2, gdr::Input<"">> {
public:
    Replay2() : Replay("Pathfinder", 1) {}
};

namespace {
    constexpr float kDt = 1.0f / 240.0f;
    constexpr int kMaxNodes = 6000;
    constexpr int kMaxFramesAhead = 2200;
    constexpr float kYLimitPadding = 200.0f;
    constexpr int kFrameBucket = 2;
    constexpr float kPosBucket = 4.0f;
    constexpr float kVelBucket = 12.0f;

    struct SearchNode {
        int id = 0;
        int parent = -1;
        int frame = 1;
        bool pressed = false;
        bool dead = false;
        float x = 0.0f;
        float y = 0.0f;
        float velocity = 0.0f;
        float fScore = std::numeric_limits<float>::max();
    };

    struct QueueEntry {
        float score;
        int nodeId;

        bool operator<(QueueEntry const& other) const {
            return score > other.score;
        }
    };

    struct SearchResult {
        std::vector<bool> pressedTimeline;
        float bestX = 0.0f;
        bool reachedGoal = false;
    };

    uint64_t discretizeState(Player const& player, int frame) {
        const auto frameBucket = static_cast<uint64_t>(std::max(0, frame / kFrameBucket));
        const auto xBucket = static_cast<uint64_t>(std::max(0, static_cast<int>(player.pos.x / kPosBucket)));
        const auto yBucket = static_cast<uint64_t>(std::max(0, static_cast<int>((player.pos.y + 4000.0f) / kPosBucket)));
        const auto vBucket = static_cast<uint64_t>(std::max(0, static_cast<int>((player.velocity + 4000.0f) / kVelBucket)));

        uint64_t key = 0;
        key |= frameBucket & 0xFFFFull;
        key |= (xBucket & 0x1FFFFull) << 16;
        key |= (yBucket & 0x1FFFFull) << 33;
        key |= (vBucket & 0x1FFull) << 50;
        key |= static_cast<uint64_t>(player.grounded) << 59;
        key |= static_cast<uint64_t>(player.upsideDown) << 60;
        key |= static_cast<uint64_t>(player.small) << 61;
        key |= static_cast<uint64_t>(player.button) << 62;
        return key;
    }

    float heuristic(Player const& player, Level const& level) {
        const auto remaining = std::max(0.0f, level.length - player.pos.x);
        const auto speedIndex = std::clamp(player.speed, 0, 4);
        const auto speed = static_cast<float>(player_speeds[speedIndex]);
        const auto framesRemaining = remaining / std::max(speed * kDt, 0.001f);
        return framesRemaining;
    }

    bool isUsable(Player const& player, float highestY, Level const& level) {
        if (player.dead) {
            return false;
        }

        if (player.pos.x < -40.0f) {
            return false;
        }

        const auto upperLimit = highestY + kYLimitPadding;
        if (player.pos.y < -kYLimitPadding || player.pos.y > upperLimit) {
            return false;
        }

        if (player.pos.x > level.length + 120.0f) {
            return false;
        }

        return true;
    }

    float findHighestY(Level const& level) {
        float highestY = 0.0f;
        for (auto const& section : level.sections) {
            for (auto const& object : section) {
                highestY = std::max(highestY, object->pos.y + object->size.y);
            }
        }
        return highestY;
    }

    std::vector<bool> reconstructTimeline(std::vector<SearchNode> const& nodes, int nodeId) {
        std::vector<bool> reversed;
        int cursor = nodeId;
        while (cursor >= 0) {
            auto const& node = nodes[cursor];
            if (node.parent >= 0) {
                reversed.push_back(node.pressed);
            }
            cursor = node.parent;
        }

        std::reverse(reversed.begin(), reversed.end());
        return reversed;
    }

    Player replayTimeline(Level& level, std::vector<bool> const& timeline) {
        level.rollback(1);
        for (bool pressed : timeline) {
            level.runFrame(pressed, kDt);
        }
        return level.latestState();
    }

    SearchResult runAStar(std::string const& levelString, std::atomic_bool& stop, std::function<void(double)> const& callback) {
        Level baseLevel(levelString);
        const float highestY = findHighestY(baseLevel);

        std::vector<SearchNode> nodes;
        nodes.reserve(kMaxNodes + 1);
        std::priority_queue<QueueEntry> open;
        std::unordered_map<uint64_t, float> bestScore;
        bestScore.reserve(kMaxNodes * 2);

        auto const& startPlayer = baseLevel.latestState();
        nodes.push_back(SearchNode{
            .id = 0,
            .parent = -1,
            .frame = 1,
            .pressed = false,
            .dead = startPlayer.dead,
            .x = startPlayer.pos.x,
            .y = startPlayer.pos.y,
            .velocity = static_cast<float>(startPlayer.velocity),
            .fScore = heuristic(startPlayer, baseLevel),
        });
        open.push({nodes.front().fScore, 0});
        bestScore[discretizeState(startPlayer, 1)] = 0.0f;

        int bestNodeId = 0;
        float bestProgress = startPlayer.pos.x;

        while (!open.empty() && static_cast<int>(nodes.size()) < kMaxNodes && !stop) {
            const auto currentId = open.top().nodeId;
            open.pop();

            const auto currentTimeline = reconstructTimeline(nodes, currentId);
            Level sim(levelString);
            auto currentPlayer = replayTimeline(sim, currentTimeline);
            const auto currentFrame = sim.currentFrame();

            if (currentPlayer.pos.x > bestProgress) {
                bestProgress = currentPlayer.pos.x;
                bestNodeId = currentId;
                if (callback) {
                    callback(std::min((bestProgress / sim.length) * 100.0, 100.0));
                }
            }

            if (currentPlayer.pos.x >= sim.length || currentFrame >= kMaxFramesAhead) {
                bestNodeId = currentId;
                bestProgress = currentPlayer.pos.x;
                break;
            }

            for (bool nextPressed : { false, true }) {
                Level branch(levelString);
                auto branchTimeline = currentTimeline;
                branchTimeline.push_back(nextPressed);
                auto nextPlayer = replayTimeline(branch, branchTimeline);
                const auto nextFrame = branch.currentFrame();

                if (!isUsable(nextPlayer, highestY, branch)) {
                    continue;
                }

                const auto gScore = static_cast<float>(branchTimeline.size());
                const auto key = discretizeState(nextPlayer, nextFrame);
                auto bestIt = bestScore.find(key);
                if (bestIt != bestScore.end() && bestIt->second <= gScore) {
                    continue;
                }
                bestScore[key] = gScore;

                SearchNode node;
                node.id = static_cast<int>(nodes.size());
                node.parent = currentId;
                node.frame = nextFrame;
                node.pressed = nextPressed;
                node.dead = nextPlayer.dead;
                node.x = nextPlayer.pos.x;
                node.y = nextPlayer.pos.y;
                node.velocity = static_cast<float>(nextPlayer.velocity);
                node.fScore = gScore + heuristic(nextPlayer, branch);

                nodes.push_back(node);
                open.push({node.fScore, node.id});
            }
        }

        SearchResult result;
        result.pressedTimeline = reconstructTimeline(nodes, bestNodeId);
        result.bestX = bestProgress;
        result.reachedGoal = bestProgress >= baseLevel.length;
        if (callback) {
            callback(std::min((result.bestX / baseLevel.length) * 100.0, 100.0));
        }
        return result;
    }

    std::vector<uint8_t> exportReplay(std::vector<bool> const& timeline) {
        Replay2 output;
        bool previous = false;
        for (size_t i = 0; i < timeline.size(); ++i) {
            const bool current = timeline[i];
            if (i == 0 || current != previous) {
                output.inputs.push_back(gdr::Input(static_cast<int>(i) + 2, 1, false, current));
            }
            previous = current;
        }
        return output.exportData().unwrapOr(std::vector<uint8_t>{});
    }

    return exportReplay(result.pressedTimeline);
}
